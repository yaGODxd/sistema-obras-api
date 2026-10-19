A Parte 3 (etapas A e B) está pronta: 63 testes passaram, 1 desligado de propósito, e o jar foi gerado de novo e mudou. Não fiz commit e não conectei ao banco.

**O que vai junto no próximo commit:** o último commit no git é "Partes 1 e 2". Ainda não estão commitados três blocos de trabalho, e eles vão juntos:
1. os limites de tamanho dos textos e o corte do texto do log;
2. a Parte 6A;
3. a Parte 3.

**A ordem importa, porque a Parte 3 precisa de SQL ANTES do deploy:** o código novo lê duas colunas que ainda não existem no banco.

## Relatório da Parte 3

**1. O que mudou**

*Etapa A: manutenção e ocorrência*
- **Status da ocorrência:**
  - `Ocorrencia` ganhou `status` e `resolvidaEm`.
  - `OcorrenciaResponse` e `OcorrenciaDetalhadaResponse` ganharam `status` e `resolvidaEm`. A resposta detalhada também traz a lista `manutencoes` (id e status de cada manutenção ligada).
  - Novos: `ManutencaoVinculadaResponse` e `AtualizarStatusOcorrenciaRequest`.
- **`OcorrenciaService`:**
  - **Regra pelas manutenções:** se todas as manutenções ligadas estão concluídas, a ocorrência vira `concluida` e guarda a hora em `resolvida_em`. Senão, fica `em_andamento`, e `resolvida_em` volta a ficar vazio.
  - **Mudança à mão:** o gestor pode mudar o status.
  - **Log:** toda mudança grava um log `OCORRENCIA_STATUS`.
  - **Filtro:** a lista aceita filtro por status.
- **`OcorrenciaController`:**
  - Nova rota `PUT /ocorrencias/{id}/status`.
  - `GET /ocorrencias?status=` filtra a lista.
- **`ManutencaoService`:** recebeu `@Transactional`, ou seja, ou grava tudo ou não grava nada.
  - **Ao abrir:** confere se a ocorrência informada existe antes de gravar.
  - **Ao abrir e ao mudar o status:** recalcula o status da ocorrência.
  - **Item 19, status do veículo:** se o veículo tem outra manutenção aberta, fica `em_manutencao`. Senão, se tem diário aberto, fica `em_uso`. Senão, fica `disponivel`.
  - **Log:** grava o login de quem está autenticado. Sem token, grava "sistema".
- **`AtualizarStatusRequest`:** o status da manutenção só aceita `aberta`, `em_andamento` ou `concluida`.

*Etapa B: os outros fluxos*
- **Transações:** abrir e fechar diário, abastecimento e registrar ocorrência passaram a gravar tudo ou nada. A lógica não mudou.
- **`LogService`:**
  - O log grava numa transação separada.
  - Se gravar o log falhar, o erro vai só para o log do servidor e não desfaz nem derruba a operação principal.
  - Ganhou `loginAutenticado()`, para saber quem fez a ação.
- **Classes abertas:** confirmei que o plugin `kotlin-spring` está no build, então os services aceitam `@Transactional`. Um teste confere a anotação em cada método.
- **Fechar diário:** recusa medidor final menor que o inicial, com a mensagem combinada.
- **Validações:** litros precisa ser maior que 0, e a quantidade de peça precisa ser pelo menos 1. Valor do abastecimento, custo de mão de obra e valor unitário da peça continuam aceitando 0.
- **Log do abastecimento:** o " (Comboio)" agora aparece mesmo quando há posto.

*Testes e documentos*
- **Testes novos:** `ManutencaoOcorrenciaTest` (8) e `EtapaBTest` (3). Mais 2 casos em `LogServiceTest` e 5 em `ErrosDasRotasTest`.
- **Documentos:** `docs/migracoes.sql`, `PLANO-API.md`, `PENDENCIAS-API.md` e `CLAUDE.md`.

**2. SQL ANTES do deploy (obrigatório)**
```sql
ALTER TABLE ocorrencias ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'aberta';
ALTER TABLE ocorrencias ADD COLUMN IF NOT EXISTS resolvida_em timestamp;
```
Pode rodar mesmo com o código antigo no ar: ele ignora as colunas novas.

O **backfill** (marcar as ocorrências antigas conforme as manutenções delas) está comentado no mesmo bloco de `docs/migracoes.sql` e espera a sua autorização. É o texto do item 18, com dois acréscimos:
- ele também preenche `resolvida_em` com a hora da última manutenção concluída;
- ele roda dentro de BEGIN/COMMIT, para você conferir antes de confirmar.

**3. Variáveis no Render:** nenhuma.

**4. Testes e jar**
- `.\gradlew.bat test`: BUILD SUCCESSFUL, 63 passaram, 0 falhas, 1 desligado.
- `.\gradlew.bat bootJar`: BUILD SUCCESSFUL. **O jar mudou: sim.**

**5. O que testar**
- Criar uma ocorrência e abrir uma manutenção com `ocorrenciaId` → `GET /ocorrencias` mostra `"status": "em_andamento"`.
- Concluir a manutenção (`PUT /manutencoes/{id}/status` com `"concluida"`) → ocorrência `concluida` e `resolvidaEm` preenchido. O veículo fica `disponivel` (ou `em_uso`, se ele tiver diário aberto).
- Com duas manutenções na mesma ocorrência, concluir só uma → a ocorrência continua `em_andamento` e o veículo continua `em_manutencao`.
- `PUT /manutencoes/{id}/status` com `"finalizada"` → 400 `{"erro":"Campo 'status' deve ser aberta, em_andamento ou concluida."}`.
- `PUT /ocorrencias/{id}/status` com `"descartada"` → 200. Com `"resolvida"` → 400. Com um id que não existe → 404 `{"erro":"Ocorrência não encontrada."}`.
- `GET /ocorrencias?status=aberta` → só as ocorrências abertas.
- `POST /diarios/fechar/{login}` com medidor menor que o inicial → 400 com a mensagem combinada.
- `POST /abastecimentos` com `"litros": 0` → 400. Com `"valorTotal": 0` → 200.
- Em `/logs`, a manutenção feita pelo front com token aparece com o login do gestor, e não mais "sistema".

**6. Impacto no front e no app**
- **Só campos novos:** nada quebra.
- **Front:** pode mostrar o status da ocorrência, o filtro, a mudança à mão e o "Vincular a uma ocorrência".
- **App:** pode mostrar o status da ocorrência e precisa recusar litros = 0 e quantidade 0 antes de enviar.
- **O que mudou de comportamento:**
  - Status de manutenção fora dos três valores, que antes era gravado, agora dá 400.
  - Medidor final menor que o inicial, que antes era gravado, agora dá 400.

**7. Como desfazer:** `git revert` do commit e novo deploy. As colunas novas podem ficar no banco, porque o código antigo funciona com elas. Não apague as colunas.

**8. Achados fora do escopo (não mexi)**
- **Log da abertura de diário:** tem o mesmo erro de parênteses do log do abastecimento. A observação some do texto quando há destino.
- **Veículo inativo pode virar disponível:** concluir uma manutenção de um veículo inativo deixa ele `disponivel`, como já acontecia antes.
- **Log de ativar/desativar usuário:** ainda grava "sistema". Dá para usar o login do token, como na manutenção.
- **Log que fica sem a operação:** como o log grava numa transação separada, se a operação principal falhar depois de o log ter sido gravado, o log fica registrado sem a operação ter acontecido. Nos fluxos atuais o log é sempre o último passo antes da resposta, então o risco é baixo.
- **Filtro de status sem conferência:** `GET /ocorrencias?status=` com um valor que não existe devolve lista vazia, sem erro.
- **Lista de ocorrências mais pesada:** a resposta detalhada faz mais uma consulta por ocorrência, para buscar as manutenções ligadas. Isso entra nas consultas repetidas da Parte 4.

**Ordem de deploy (vale para os três blocos juntos):**
1. Rodar o SQL do item 2.
2. Commitar com o jar e dar push.
3. Esperar o Render subir e testar o item 5.
4. Depois do deploy, rodar o PASSO 0 e o PASSO 1 da 6A, usando como corte a hora deste deploy, e me mandar os resultados.

Paro aqui. Quando quiser, autorize o backfill da Parte 3, me mande o resultado do PASSO 1 da 6A, ou escreva "pode começar a parte 4".
