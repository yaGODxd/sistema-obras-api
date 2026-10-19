# API SGFE (Kotlin + Spring Boot 4.1, PostgreSQL no Render)

O trabalho segue `docs/PLANO-API.md` (releia após compactar o contexto). Pendências vivas em `docs/PENDENCIAS-API.md`.

## Comandos (Windows)
- Testes: `.\gradlew.bat test` (só @WebMvcTest e mocks; nenhum teste acessa banco)
- Jar: `.\gradlew.bat bootJar` — o jar em `build/libs/` é versionado de propósito (o Dockerfile copia ele). Nunca rodar bootRun nem teste com contexto completo apontando para o banco.

## Convenções
- Erros: lance `RegraNegocioException`, `NaoEncontradoException`, `ConflitoException` ou `NaoAutorizadoException` (pacote `exception`). O `TratadorDeErros` responde sempre `{"erro": "mensagem"}`; erro inesperado vira 500 com mensagem genérica e stack só no log.
- `@StatusErroFixo(N)` em rota antiga = contrato: qualquer erro dela responde N (o app/front dependem disso). Tirar a anotação muda status: só com OK do Yago.
- Nada de try/catch que engole exceção nos controllers.
- Validação de entrada: anotações `@field:` nos DTOs com textos de `MensagensValidacao` e `@Valid` no `@RequestBody`. Não validar `LoginRequest` (mudaria o 401 do login para 400).
- Banco: esquema manual (`ddl-auto` padrão `none`). Mudança de banco = SQL idempotente em `docs/migracoes.sql`, rodado pelo Yago antes do deploy.
- Compatibilidade: só acrescentar campos; não mudar status de sucesso.
- Variáveis: `SPRING_DATASOURCE_*`, `JWT_SECRET`, `JWT_EXPIRATION`, `SPRING_JPA_HIBERNATE_DDL_AUTO` (padrão none), `SPRING_JPA_SHOW_SQL` (padrão false), `APP_VALIDAR_CPF_DIGITOS` (padrão false).
