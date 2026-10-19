Com as consultas, o desenho da 6B fica fechado. Mas elas também mostraram quatro problemas que já existem hoje, fora da 6B. Registrei todos no item 21 de `docs/PENDENCIAS-API.md`. Não mexi no código nem no banco.

## 1. Achados que valem para agora

**a) Status das ocorrências.** A coluna `ocorrencias.status` **já existia** antes da Parte 3. Ela é texto, mas o valor padrão vem de um enum antigo: `'pendente'::status_ocorrencia`, com os valores `pendente`, `em_andamento` e `resolvida`.
- O `ADD COLUMN IF NOT EXISTS` da Parte 3 não fez nada nessa coluna. Só o `resolvida_em` foi criado.
- Ocorrências antigas devem estar como `pendente`, valor que a API não usa. Uma ocorrência nova sem o código da Parte 3 também nasce `pendente`.
- **Efeito:** o filtro `?status=aberta` não encontra essas ocorrências. Elas aparecem com um status que o front não conhece.
- **Correção proposta** (depende do seu OK no texto exato):
  ```sql
  ALTER TABLE ocorrencias ALTER COLUMN status SET DEFAULT 'aberta';
  -- backfill (BEGIN/COMMIT):
  UPDATE ocorrencias SET status = 'aberta'    WHERE status = 'pendente';
  UPDATE ocorrencias SET status = 'concluida' WHERE status = 'resolvida';
  ```
  Depois disso, o backfill da Parte 3 (o status calculado pelas manutenções) continua valendo como está.

**b) Placa.** O banco **já tem** um índice único na placa (`veiculos_placa_key`).
- Ele compara a placa exata e inclui veículos inativos.
- Por isso, a decisão da Parte 2 de liberar a placa de um veículo inativo não funciona na prática: o banco recusa o cadastro, e a API responde "Já existe um veículo com esta placa.".
- Ao mesmo tempo, o banco aceita "ABC-1D23" e "ABC1D23" como placas diferentes. Quem barra essa duplicata é a API.
- **Escolha uma:**
  - **manter como está:** inativo continua segurando a placa. É o que acontece hoje. Assim a reativação nunca vai conflitar;
  - **trocar o índice:** passar a usar o índice parcial sugerido na Parte 2. Isso exige apagar o índice atual (DROP INDEX), e só com a sua aprovação.

  **Recomendo manter.**

**c) Perfil "administrador".** O banco aceita um perfil `administrador`, mas o código só conhece `gestor` e `motorista`.
- Se existir algum usuário administrador, `GET /usuarios` quebra ao carregar a lista.
- Com a matriz de permissões ligada, esse usuário não teria acesso a nada.
- **Pergunta:** algum usuário é administrador? Para conferir: `SELECT login, CAST(perfil AS text) FROM usuarios;`.

**d) Triggers do diário.** Os triggers cuidam de parte do trabalho ao abrir e fechar o diário.
- **Ao abrir diário,** `fn_abre_diario` marca o veículo como `em_uso` e grava o histórico.
- **Ao fechar diário,** `fn_fecha_diario` calcula o `medidor_percorrido`, grava **`veiculos.medidor_atual = medidor_final`**, marca o veículo como `disponivel` e grava o histórico. Isso responde a dúvida sobre quem atualiza o medidor do veículo.
- **Problema 1, manutenção:** o fechamento devolve o veículo para `disponivel` mesmo com uma manutenção aberta. É o mesmo problema que a Parte 3 corrigiu para manutenções.
- **Problema 2, fila offline:** se um fechamento antigo chegar depois de um mais novo, o `medidor_atual` volta para trás.
- **Proposta para a 6B:**
  - mudar o trigger para gravar o medidor só quando ele aumentar (`GREATEST`). É um `CREATE OR REPLACE FUNCTION`, que só roda com o seu OK no texto exato;
  - no código, depois do fechamento, calcular o status do veículo como na Parte 3: manutenção aberta → `em_manutencao`.

**Outros pontos**
- **Checklist:** o checklist já tem um índice único por diário e item, e hoje um reenvio dá erro 500. Na 6B isso vira a idempotência do checklist, sem coluna nova: um reenvio só atualiza a resposta.
- **Colunas de data:** todas são "timestamp without time zone", então o PASSO 0 da 6A está ok para abastecimentos e ocorrências.
- **Volume:** é pequeno (246 pontos de rastreamento), então o lote não pesa.
- **Fotos:** existe uma tabela `fotos` ainda sem uso. Ela serve para o item 14, fotos de comprovação, mais adiante.

## 2. Desenho final da 6B (SQL proposto, ainda não gravado)

Tudo é acréscimo, com IF NOT EXISTS, e funciona com o código antigo.

```sql
-- Turnos e diários: dois eventos (abertura e fechamento)
ALTER TABLE turnos        ADD COLUMN IF NOT EXISTS aberto_recebido_em timestamp,
                          ADD COLUMN IF NOT EXISTS fechado_recebido_em timestamp,
                          ADD COLUMN IF NOT EXISTS id_local_abertura uuid,
                          ADD COLUMN IF NOT EXISTS id_local_fechamento uuid;
ALTER TABLE diarios_bordo ADD COLUMN IF NOT EXISTS aberto_recebido_em timestamp,
                          ADD COLUMN IF NOT EXISTS fechado_recebido_em timestamp,
                          ADD COLUMN IF NOT EXISTS id_local_abertura uuid,
                          ADD COLUMN IF NOT EXISTS id_local_fechamento uuid,
                          ADD COLUMN IF NOT EXISTS horario_suspeito boolean NOT NULL DEFAULT false,
                          ADD COLUMN IF NOT EXISTS motivo_suspeita varchar(255);
-- Eventos simples (mesmo bloco para abastecimentos, ocorrencias e rastreamento_gps)
ALTER TABLE abastecimentos ADD COLUMN IF NOT EXISTS recebido_em timestamp,
                           ADD COLUMN IF NOT EXISTS id_local uuid,
                           ADD COLUMN IF NOT EXISTS desvio_relogio_seg integer,
                           ADD COLUMN IF NOT EXISTS horario_suspeito boolean NOT NULL DEFAULT false,
                           ADD COLUMN IF NOT EXISTS motivo_suspeita varchar(255);
ALTER TABLE checklist_respostas ADD COLUMN IF NOT EXISTS recebido_em timestamp;
-- Reenvio sem duplicar: um índice único parcial por id_local, em cada tabela
CREATE UNIQUE INDEX IF NOT EXISTS uq_abastecimentos_id_local ON abastecimentos (id_local) WHERE id_local IS NOT NULL;
-- (o mesmo para ocorrencias, rastreamento_gps, e para id_local_abertura/fechamento de turnos e diarios_bordo)
CREATE INDEX IF NOT EXISTS idx_rastreamento_recebido ON rastreamento_gps (recebido_em);
```

**Como o código vai usar isso:**
- **Hora do evento:** as colunas que já existem (`aberto_em`, `fechado_em`, `registrado_em`) passam a guardar a hora do evento, do celular. As colunas `*recebido_em` guardam a hora do servidor.
- **Registros antigos:** ficam com `recebido_em` vazio. O `/rastreamento/posicoes` usa `COALESCE(recebido_em, registrado_em)`.
- **Assinatura:** o resumo assinado não muda.

As regras, as rotas novas, os status e os códigos de erro continuam como na proposta anterior (enviada na conversa).

## 3. Respostas que preciso para começar
1. **Tolerâncias:** 5 minutos para horário no futuro e 7 dias para horário antigo. Pode ser?
2. **Desvio do relógio:** só marcar como suspeito (minha recomendação) ou também corrigir o horário?
3. **Fora do período do diário:** recusar com 422 (minha recomendação) ou só marcar como suspeito?
4. **`idLocal` no turno:** incluir? Recomendo que sim, já está no SQL acima.
5. **Corte de dia e de mês em Brasília e datas com fuso nas respostas:** entram na 6B ou ficam numa parte separada?
6. **Ocorrências (achado a):** aprova o SQL de correção?
7. **Placa (achado b):** manter o índice atual?
8. **Administrador (achado c):** existe algum usuário com esse perfil?
9. **Trigger do medidor (achado d):** aprova trocar para `GREATEST` e calcular o status do veículo no fechamento?

Quando você responder, gravo o SQL em `docs/migracoes.sql` e começo o código da 6B.
