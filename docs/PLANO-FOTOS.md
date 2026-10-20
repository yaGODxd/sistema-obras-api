# Plano da Parte 9: fotos guardadas no banco (proposta, aguardando aprovação)

Li o `docs/PARTE-9-FOTOS-API.md`. Abaixo vão o plano, o SQL proposto e as dúvidas. Não codifiquei, não rodei SQL e não mexi em nenhum arquivo.

## Antes de fechar o SQL, preciso de 3 consultas (só leitura)
```sql
-- Q1: índices, chaves estrangeiras e restrições da tabela fotos
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'fotos'::regclass;
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'fotos';
-- Q2: colunas de motivos_uso (para saber se existe "ativo")
SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_name = 'motivos_uso';
-- Q3: versão do PostgreSQL e espaço já usado
SELECT version(), pg_size_pretty(pg_database_size(current_database()));
```

## Decisões propostas (pontos a até i)

**a) Guardar como `bytea`** (o mesmo que o coordenador prefere).
- **Por quê:** ocupa 33% menos que base64.
- **O conteúdo nunca chega numa lista:** a tabela `fotos` não vira entidade JPA com o conteúdo mapeado. O "carregar só quando precisar" do Hibernate não é confiável sem uma configuração extra.
- **Como fica a leitura e a gravação:** tudo por consultas SQL escritas à mão.
  - a lista de metadados seleciona colunas explícitas, sem `conteudo` e sem `SELECT *`;
  - só `GET /fotos/{id}` lê o conteúdo;
  - a gravação recebe os bytes direto.
- **Teste de proteção:** um teste confere que a consulta da lista não contém `conteudo`.
- **Isolamento:** o acesso fica atrás de uma interface `ArmazenamentoFotos`, com `gravar` e `ler`, para poder trocar depois (ponto g).

**b) `caminho_arquivo` passa a ser opcional.** `ALTER COLUMN caminho_arquivo DROP NOT NULL`. A tabela está vazia e nenhum código usa a coluna. Ela fica reservada para o armazenamento externo no futuro.

**c) Tipos de foto.** Acrescentar `cupom_fiscal` e `bomba_comboio` ao enum `tipo_foto` com `ALTER TYPE ... ADD VALUE IF NOT EXISTS`.
- **Cuidados:**
  - rodar **antes** do deploy;
  - rodar fora de transação: um valor novo não pode ser usado na mesma transação em que foi criado;
  - a API valida a lista antes de gravar, para não repetir o erro do status do veículo.
- **Vínculo de cada tipo:**

| Tipo | Vínculo exigido |
|---|---|
| `medidor_abertura`, `medidor_fechamento` | só o diário |
| `ocorrencia` | `ocorrenciaId`, de uma ocorrência do mesmo diário |
| `cupom_fiscal`, `bomba_comboio` | `abastecimentoId`, de um abastecimento do mesmo diário |
| `livre` | só o diário; o vínculo é opcional |

  Vínculo errado: 400 `VINCULO_INVALIDO`.

**d) SQL (proposto, não rodei):**
```sql
-- 1) fora de transação
ALTER TYPE tipo_foto ADD VALUE IF NOT EXISTS 'cupom_fiscal';
ALTER TYPE tipo_foto ADD VALUE IF NOT EXISTS 'bomba_comboio';
-- 2)
BEGIN;
ALTER TABLE fotos
    ALTER COLUMN caminho_arquivo DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS conteudo            bytea,
    ADD COLUMN IF NOT EXISTS tipo_mime           varchar(20),
    ADD COLUMN IF NOT EXISTS tamanho_bytes       integer,
    ADD COLUMN IF NOT EXISTS hash_sha256         char(64),
    ADD COLUMN IF NOT EXISTS id_local            uuid,
    ADD COLUMN IF NOT EXISTS recebida_em         timestamp,
    ADD COLUMN IF NOT EXISTS registrado_por      uuid,
    ADD COLUMN IF NOT EXISTS abastecimento_id    uuid REFERENCES abastecimentos(id),
    ADD COLUMN IF NOT EXISTS desvio_relogio_seg  integer,
    ADD COLUMN IF NOT EXISTS horario_suspeito    boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS motivo_suspeita     varchar(255);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fotos_id_local ON fotos (id_local) WHERE id_local IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_fotos_diario ON fotos (diario_id);   -- se a Q1 mostrar que não existe
COMMIT;
```
- **Hora da foto:** reaproveito a coluna `tirada_em`, que já existe, como `ocorrido_em`, em vez de criar outra (ver dúvida 1).
- **Colunas de horário suspeito:** acrescentei `horario_suspeito`, `motivo_suspeita` e `desvio_relogio_seg` para seguir o padrão dos outros eventos.
- **Rollback:**
  - apagar as colunas novas;
  - `SET NOT NULL` só se nenhuma linha tiver ficado sem caminho;
  - **valor de enum não sai:** o Postgres não remove valor de enum. Os dois valores ficam, sem efeito.

**e) Limites e memória**
- **Tamanho:** `spring.servlet.multipart.max-file-size=${APP_FOTO_MAX_ARQUIVO:10MB}` e `max-request-size=${APP_FOTO_MAX_REQUISICAO:11MB}`. O arquivo enviado vai para disco temporário, não fica todo na memória.
- **Arquivo grande demais:** 413 `FOTO_GRANDE_DEMAIS`.
- **Tipo do arquivo:** a API olha os primeiros bytes, não o nome. JPEG começa com `FF D8 FF` e PNG com `89 50 4E 47 0D 0A 1A 0A`. Outro tipo: 415 `TIPO_ARQUIVO_INVALIDO`. Arquivo vazio: 400.
- **Pouca memória na Render:** no máximo 2 envios de foto ao mesmo tempo (`APP_FOTO_ENVIOS_SIMULTANEOS:2`). O terceiro recebe 503 `FOTO_OCUPADO` e o app tenta de novo.
- **Leitura:** a imagem é lida inteira, até 10 MB. É aceitável com esse limite de 2.

**f) Assinatura do diário**
- **Agora (o mesmo que o coordenador prefere):** guardar o sha256 de cada foto. O resumo assinado não muda.
  - **Custo:** zero.
  - **Limite:** uma foto alterada direto no banco não seria detectada.
  - **Proteção parcial:** a API não terá rota para apagar nem trocar foto, e diário assinado recusa foto nova (409 `DIARIO_ASSINADO`).
- **Depois, numa etapa pequena:** acrescentar ao resumo assinado uma linha com o hash de cada foto, **só quando o diário tiver foto**.
  - Os diários já assinados, sem foto, continuam íntegros.
  - **Custo:** o app precisa enviar todas as fotos **antes** de assinar.
  - A verificação passa a ler os hashes das fotos (só os hashes, nunca as imagens).

**g) Espaço**
- **Conta:** o banco gratuito tem cerca de 1 GB. Com fotos de 300 a 600 KB (o app comprime), cabem de 1.500 a 3.000 fotos no espaço livre. A Q3 dá o número real.
- **Exemplo de ritmo:** com 5 motoristas, 2 diários por dia e 3 fotos por diário, são cerca de 30 fotos por dia. O espaço acaba em 2 a 3 meses.
- **Rota proposta:** `GET /fotos/uso`, só para gestor. Devolve quantidade, soma de `tamanho_bytes`, tamanho da tabela e do banco.
- **Consulta equivalente:** `SELECT COUNT(*), pg_size_pretty(SUM(tamanho_bytes)::bigint), pg_size_pretty(pg_total_relation_size('fotos')), pg_size_pretty(pg_database_size(current_database())) FROM fotos;`.
- **Trocar para armazenamento externo depois:** uma nova implementação de `ArmazenamentoFotos` grava fora e guarda a chave em `caminho_arquivo`, com `conteudo` vazio. As rotas não mudam, então app e front também não mudam.

**h) Fotos no navegador.** O `<img src>` não manda o token. Com a matriz de permissões ligada, a imagem daria 401.
- **Proposta:** o front busca a imagem pelo `HttpClient`, com `responseType: 'blob'`. O interceptor de token funciona normalmente. Depois cria a URL com `URL.createObjectURL(blob)`, usa no `<img>` e libera com `revokeObjectURL` ao sair da tela.
- **Cache:** a API responde com `Cache-Control: private, max-age=86400`, mais `ETag` com o sha256, para não baixar de novo.
- **Alternativa:** um link assinado temporário. Funciona, mas é mais complexo; não proponho agora.

**i) Testes pelo Swagger**
1. Enviar JPEG válido → 200 com os metadados.
2. Reenviar com o mesmo `idLocal` → 200, mesma foto, sem duplicar.
3. Mesmo `idLocal` usado por outro motorista → 409 `ID_LOCAL_EM_USO`.
4. Foto para diário assinado → 409 `DIARIO_ASSINADO`.
5. Arquivo de 12 MB → 413 `FOTO_GRANDE_DEMAIS`.
6. PDF renomeado para .jpg → 415 `TIPO_ARQUIVO_INVALIDO`.
7. `tipo` desconhecido, ou `cupom_fiscal` sem `abastecimentoId` → 400.
8. `GET /diarios/{id}/fotos` → lista sem bytes.
9. `GET /fotos/{id}` → a imagem, com `Content-Type` certo.
10. Motorista tentando ver a foto de outro diário → 403.

## Rotas e status
- **`POST /fotos`** (multipart, só motorista):

| Status | Quando |
|---|---|
| 200 | Gravou, ou é reenvio com o mesmo `idLocal` |
| 400 | Dado inválido ou `VINCULO_INVALIDO` |
| 403 | Diário de outro motorista |
| 404 | Diário, ocorrência ou abastecimento não existe |
| 409 | `DIARIO_ASSINADO`, `ID_LOCAL_EM_USO` ou `REENVIO_SIMULTANEO` |
| 413 | `FOTO_GRANDE_DEMAIS` |
| 415 | `TIPO_ARQUIVO_INVALIDO` |
| 422 | `HORARIO_FUTURO` |
| 503 | `FOTO_OCUPADO` |

- **`GET /fotos/{id}`:** a imagem. Gestor vê qualquer uma; motorista, só do próprio diário.
- **`GET /diarios/{id}/fotos`:** só os metadados. Mesma regra de permissão.
- **`GET /fotos/uso`:** só gestor.

## Extra: `GET /motivos-uso`
- **O que faz:** devolve `id` e `descricao`, ordenados pela descrição. Gestor e motorista podem usar.
- **Coluna `ativo`:** se a Q2 mostrar que a tabela não tem essa coluna, listo todos os motivos e, se quiser, proponho `ADD COLUMN IF NOT EXISTS ativo boolean NOT NULL DEFAULT true`.
- **No app:** o campo de texto livre "motivo" vira uma lista de escolha que envia `motivoUsoId`.

## Etapas (uma por vez, com relatório)
1. **`GET /motivos-uso`.** Pequena e independente. Sem SQL, ou só o `ativo`.
2. **SQL das fotos + `POST /fotos`:** validações, limites, controle de envios simultâneos, `idLocal`, `HorarioEvento`, matriz de permissões e Swagger.
3. **Leitura:** `GET /fotos/{id}`, `GET /diarios/{id}/fotos` e `GET /fotos/uso`, com testes.
4. **Depois, se aprovado:** a assinatura passa a cobrir as fotos.

**Variáveis novas:** `APP_FOTO_MAX_ARQUIVO` (10MB), `APP_FOTO_MAX_REQUISICAO` (11MB) e `APP_FOTO_ENVIOS_SIMULTANEOS` (2).

**Impacto**
- **App:** enviar o evento, guardar o `id` devolvido e depois mandar a foto com `idLocal` próprio. Tratar 413 e 415 como erro permanente e 503 como "tentar de novo". Usar a lista de motivos.
- **Front:** mostrar as fotos via blob com token (ponto h) e mostrar a lista de cada diário.

**Riscos**
- O banco gratuito enche em poucos meses (ponto g).
- O backup fica bem maior.
- A Render tem pouca memória (por isso o limite de 2 envios simultâneos).

**O que NÃO vou fazer**
- Reduzir ou converter foto.
- Apagar ou trocar foto.
- Mexer na foto de perfil ou na `foto_nota_fiscal` da manutenção.
- Mudar o resumo da assinatura nesta parte.
- Usar as colunas `sincronizado`.

## Dúvidas
1. Posso reaproveitar `tirada_em` como a hora da foto, em vez de criar `ocorrido_em`?
2. Acrescentar valores ao enum `tipo_foto` está ok? A alternativa é trocar a coluna para texto com CHECK (a tabela está vazia).
3. Limite de 10 MB e no máximo 2 envios simultâneos estão ok?
4. Os status 413, 415 e 503 nas rotas novas estão ok? A alternativa é 400 com o código.
5. O gestor também envia foto, ou só o motorista?
6. A foto da nota fiscal da manutenção continua como está (base64 em `manutencoes`)?
7. A etapa 4 (assinatura cobrindo as fotos) entra agora ou depois?

Paro aqui e espero a aprovação e os resultados de Q1, Q2 e Q3.

---

## Aprovação e ajustes (19/10/2026)

Plano aprovado pelo coordenador, com estes ajustes (valem sobre o texto acima):

- **Respostas às dúvidas:** `tirada_em` é a hora da foto, sempre gravada explícita em UTC (sem depender do `now()`);
  valores novos no enum `tipo_foto`; 413, 415 e 503 com código; só o motorista envia foto; `foto_nota_fiscal`
  da manutenção fica como está; a assinatura cobrindo as fotos fica para outra parte.
- **Limite (atualização):** o app vai reduzir as fotos (lado maior 1920 px, JPEG 80, 400 a 800 KB).
  `APP_FOTO_MAX_ARQUIVO` padrão **5MB** e `APP_FOTO_MAX_REQUISICAO` **6MB** (configuráveis). 2 envios simultâneos.
- **Comboio (regra nova):** para `cupom_fiscal` e `bomba_comboio`, `abastecimentoId` é obrigatório e o diário da foto
  vem do abastecimento (`diarioId` opcional; se vier diferente, 400 `VINCULO_INVALIDO`). Permissão: o motorista do token
  é o `registrado_por` do abastecimento **ou** o dono do diário. Abastecimentos antigos (sem `registrado_por`): só o dono.
  Diário assinado continua recusando (409 `DIARIO_ASSINADO`).
- **Matriz de permissões:** `GET /fotos/uso` (só gestor) entra ANTES de `GET /fotos/*` (ambos), senão "uso" casa como id;
  `GET /diarios/*/fotos` ganha regra própria (ambos) antes das regras de `/diarios/*`; `POST /fotos` só motorista.
- **SQL da etapa 2:** sem `idx_fotos_diario` (já existe, consulta Q1). Só roda depois que o coordenador aprovar o texto exato.
- **Ordem:** etapa 1 (`GET /motivos-uso`) FEITA em 19/10; relatório e parada depois de cada etapa; sem commit.

### Conta de espaço refeita

Banco hoje: 9,8 MB (consulta Q3). Banco gratuito: cerca de 1 GB, então sobram cerca de 1.014 MB.
Ritmo de exemplo: 5 motoristas × 2 diários por dia × 3 fotos = 30 fotos por dia. JPEG quase não comprime no banco.

| Tamanho por foto | Fotos que cabem | Dias até encher (30 fotos/dia) |
|---|---|---|
| 0,4 MB | ~2.500 | ~84 dias (quase 3 meses) |
| 0,8 MB | ~1.250 | ~42 dias (1 mês e meio) |

Conclusão: mesmo com a redução no app, o banco gratuito enche em 1,5 a 3 meses. A rota `GET /fotos/uso` serve para
acompanhar; o plano de trocar para armazenamento externo (atrás das mesmas rotas) deve ser decidido antes disso.
O backup do banco também cresce na mesma proporção.

### Andamento (19/10)
- Etapa 1 (GET /motivos-uso): feita.
- Etapa 2 (POST /fotos): feita no código. SQL no bloco "Parte 9 (fotos no banco) - etapa 2" de docs/migracoes.sql (bloco (g) do para-revisao.txt), aguardando aprovação; rodar ANTES do deploy.
- Etapa 3 (GET /fotos/{id}, GET /diarios/{id}/fotos, GET /fotos/uso): feita no código.
- Etapa 4 (assinatura cobrindo as fotos): outra parte.
