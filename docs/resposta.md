A primeira metade da 6C (cortes de dia e de mês no fuso de Brasília) está pronta. A segunda metade, datas com fuso nas respostas, ficou como proposta e espera o seu OK, porque pode quebrar o app. Não fiz commit e não conectei ao banco.

## Relatório da 6C (corte de dia e de mês)

**1. O que mudou**
- **`/logs`:** os filtros `inicio` e `fim` passam a ser dias de Brasília.
  - O banco guarda UTC. Por isso, o dia 19 em Brasília vira o intervalo de 19/10 03:00 até 20/10 02:59:59 em UTC.
  - Antes, um log das 22h de Brasília caía no dia seguinte.
- **Diários (`/diarios`, `/diarios/resumo-mensal`, `/diarios/meus`):** o filtro de mês e ano passou a usar a hora de Brasília. Um diário aberto às 22h do dia 31 em Brasília já é 01h do dia 1º em UTC e ia para o mês seguinte. Agora fica no mês certo.
- **Resumo semanal do motorista:** não mudou, porque conta os últimos 7 dias a partir de agora e isso não depende de fuso.
- **Teste:** 1 novo em `LogServiceTest`, conferindo o intervalo UTC de um dia de Brasília.
- **Docs:** `PLANO-API.md` (seção 6C) e `PENDENCIAS-API.md` (itens 5 e 6).

**2. SQL:** nenhum.
**3. Variáveis no Render:** nenhuma.

**4. Testes e jar**
- `.\gradlew.bat test`: BUILD SUCCESSFUL, 121 passaram, 0 falhas, 1 desligado.
- `.\gradlew.bat bootJar`: BUILD SUCCESSFUL. **O jar mudou: sim.**

**5. O que testar**
- `GET /logs?inicio=2026-10-19&fim=2026-10-19` → inclui um log feito às 23h de Brasília do dia 19 (02h UTC do dia 20) e não inclui um das 23h do dia 18.
- `GET /diarios?mes=10&ano=2026` → um diário aberto às 22h de 31/10 (Brasília) aparece em outubro.

**6. Impacto:** nenhum no front nem no app. Os filtros continuam iguais e os resultados ficam certos para quem está em Brasília.

**7. Como desfazer:** `git revert` do commit.

**Ordem:** este código está no mesmo diretório que a 6B, então sobe junto com ela, depois do SQL 1.

## Proposta: datas com fuso nas respostas (precisa do seu OK)

**Hoje:** desde a 6A, todas as datas saem em UTC, mas sem a indicação de fuso. Por exemplo, `"2026-10-19T12:00:00"`.
- **Front:** o interceptor dele trata texto sem fuso, então está certo.
- **App:** não sei como ele lê essas datas. Se ele trata o texto como hora de Brasília, hoje está mostrando os horários 3 h adiantados.

**Proposta:** acrescentar o `Z` ao fim das datas das respostas, por exemplo `"2026-10-19T12:00:00Z"`. Isso ficaria atrás de um interruptor novo, `APP_DATAS_COM_FUSO`, que começa desligado.
- **Desligado:** as datas saem exatamente como hoje.
- **Ligado:** todas as datas das respostas saem com `Z`.

**Riscos**
- **Front:** sem risco. O interceptor só mexe em texto sem fuso, e o JavaScript lê o `Z` corretamente.
- **App:** se ele lê as datas com `LocalDateTime.parse`, **quebra** com o `Z`. Antes de ligar, o app precisa passar a usar `Instant` ou `OffsetDateTime`.
- **Volta atrás:** se algo der errado, basta pôr `APP_DATAS_COM_FUSO=false` no Render, sem novo jar.

**Perguntas para o Claude Code do app**
1. Como o app lê as datas que vêm da API: `LocalDateTime.parse`, `Instant` ou outra forma?
2. Ele trata a data recebida como UTC ou como hora local?

Paro aqui. Se aprovar a proposta, implemento o interruptor (desligado). Depois disso vem a Parte 7 (Swagger).
