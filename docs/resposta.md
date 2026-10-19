Comecei a 6B e parei de propósito depois da etapa 0, porque há um risco de deploy: se o SQL da 6B rodar com o código atual no ar, quatro telas quebram (três listas de abastecimento e as respostas do checklist). Não fiz commit e não conectei ao banco.

**O problema:** essas consultas buscavam todas as colunas da tabela (`SELECT a.*`) e liam o resultado pela posição de cada coluna. O SQL da 6B acrescenta colunas novas no fim das tabelas `abastecimentos` e `checklist_respostas`, e isso desloca as posições.

As telas afetadas:
- `GET /abastecimentos`, `/abastecimentos/veiculo/{id}` e `/abastecimentos/motorista/{login}`;
- `GET /checklist/respostas/diario/{id}`.

Como a regra é que o SQL rode antes do deploy, essas telas ficariam quebradas até o código novo subir.

## Etapa 0 (pronta)
- **Colunas explícitas:** as quatro consultas agora listam as colunas uma por uma. Funcionam com o banco de hoje e com as colunas novas.
- **Fechar diário:** o código deixou de forçar o veículo para `disponivel` depois do fechamento.
  - Com o trigger atual não muda nada, porque o próprio trigger já faz isso.
  - Com o trigger novo, o código não vai mais desfazer o `em_manutencao`.
- **SQL da 6B gravado para revisão** em `docs/migracoes.sql`, todo comentado:
  - **SQL 1:** colunas e índices com os ajustes do coordenador. Inclui `registrado_por` em abastecimentos, que guarda quem registrou (no comboio, não é o dono do diário). Está marcado como proposta, esperando o OK dele.
  - **SQL 2:** os dois triggers novos, com o texto exato, mais o texto original de cada um como rollback.
  - **Topo do arquivo:** a nota de que as colunas de data guardam UTC.
- **Testes:** 96 passaram, 0 falhas, 1 desligado. **O jar mudou: sim.**
- **Docs:** `PLANO-API.md` (andamento da 6B) e `PENDENCIAS-API.md` (achado).

## Ordem de deploy
1. **Agora, o lote:** PASSO 1 + etapa 0, com tudo o que está no diretório.
   - Rodar o diagnóstico do bloco "Parte 5" (só leitura).
   - Rodar o bloco "Ocorrências: vocabulário do banco" e terminar com `COMMIT`.
   - Commitar com o jar, dar push e testar as telas de abastecimento e de checklist.
2. **Depois:** o SQL 1 da 6B.
3. **Por último:** o deploy do código da 6B.
4. **SQL 2 (triggers):** não depende dessa ordem. Rode só depois da revisão do coordenador.

**Por que parei aqui:** se eu escrevesse o código da 6B agora, ele ficaria no mesmo diretório que o lote. Você teria que separar os commits na mão para fazer o passo 1 sozinho.

**Um detalhe para o coordenador:** `/rastreamento/posicoes` não tem filtro de tempo. Ele pega o ponto mais recente de cada diário aberto, ordenando pela hora do evento. Para mostrar a posição atual, a hora do evento é a ordem correta, então proponho não usar o `COALESCE(recebido_em, ...)` ali. Ele continua sendo usado no descarte de pontos próximos.

Quando o lote estiver no ar, escreva "continue a 6B" e eu sigo com o código.
