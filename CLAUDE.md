# API SGFE (Kotlin + Spring Boot 4.1, PostgreSQL no Render)

O trabalho segue `docs/PLANO-API.md` (releia após compactar o contexto). Pendências vivas em `docs/PENDENCIAS-API.md`.

## Comandos (Windows)
- Testes: `.\gradlew.bat test` (só @WebMvcTest e mocks; nenhum teste acessa banco)
- Jar: `.\gradlew.bat bootJar` — o jar em `build/libs/` é versionado de propósito (o Dockerfile copia ele). Nunca rodar bootRun nem teste com contexto completo apontando para o banco.

## Convenções
- Erros: lance `RegraNegocioException`, `NaoEncontradoException`, `ConflitoException` ou `NaoAutorizadoException` (pacote `exception`). O `TratadorDeErros` responde sempre `{"erro": "mensagem"}`; erro inesperado vira 500 com mensagem genérica e stack só no log.
- `@StatusErroFixo(N)` em rota antiga = contrato: qualquer erro dela responde N (o app/front dependem disso). Tirar a anotação muda status: só com OK do Yago.
- Nada de try/catch que engole exceção nos controllers.
- Escrita com vários passos: `@Transactional` no método do service. O `LogService.registrar` grava em transação própria e nunca lança (falha vai só para o log do servidor). Autor do log: `logService.loginAutenticado() ?: "sistema"`.
- Segurança: matriz rota x perfil em `SecurityConfig.matrizDePermissoes` (só vale com `APP_SEGURANCA_EXIGIR_JWT=true`). Rota nova precisa entrar na matriz. Em rotas com `{login}` chame `ControleAcesso.conferirProprioLogin(login)`; em corpos com `loginMotorista` use `ControleAcesso.loginEfetivo(...)`.
- Rotas novas não usam `@StatusErroFixo`; id inválido com `uuidValido(...)` (400).
- Erro que o app precisa tratar de forma especial: `RegraNegocioException(msg, CodigosErro.X)` → corpo `{"erro", "codigo"}`. Nunca renomear um código existente.
- Eventos do motorista (Parte 6B): horário com `HorarioEvento.resolver(...)` (hora do evento em UTC + recebido + suspeita); diário do evento com `DiarioDoEvento` (dono, assinado, janela). idLocal: reenvio devolve o já gravado; de outro dono 409 `ID_LOCAL_EM_USO`.
- Nunca usar `SELECT x.*` em consulta lida por posição (coluna nova desloca os índices); listar as colunas.
- Ocorrência: status varchar com o vocabulário do banco (`StatusOcorrencia`: pendente, em_andamento, resolvida). Manutenção é outro vocabulário (aberta, em_andamento, concluida). Nunca SET DEFAULT 'aberta'. Quem muda manutenção chama `ocorrenciaService.atualizarPelaManutencao(...)`.
- Validação de entrada: anotações `@field:` nos DTOs com textos de `MensagensValidacao` e `@Valid` no `@RequestBody`. Não validar `LoginRequest` (mudaria o 401 do login para 400).
- Banco: esquema manual (`ddl-auto` padrão `none`). Mudança de banco = SQL idempotente em `docs/migracoes.sql`, rodado pelo Yago antes do deploy.
- Compatibilidade: só acrescentar campos; não mudar status de sucesso.
- Horário: banco guarda UTC. Use `LocalDateTime.now(java.time.ZoneOffset.UTC)`; epoch do app converte com `ZoneOffset.UTC`. O main() fixa o fuso em UTC e cada conexão faz `SET TIME ZONE 'UTC'`. A hora da assinatura do diário é sempre a do servidor.
- Variáveis: `SPRING_DATASOURCE_*`, `JWT_SECRET`, `JWT_EXPIRATION`, `SPRING_JPA_HIBERNATE_DDL_AUTO` (padrão none), `SPRING_JPA_SHOW_SQL` (padrão false), `APP_VALIDAR_CPF_DIGITOS` (padrão false), `APP_SEGURANCA_EXIGIR_JWT` (padrão false), `APP_CORS_ORIGENS` (padrão *), `APP_BLOQUEIOS_DIARIO` (padrão true), `APP_DATAS_COM_FUSO` (padrão false), `SWAGGER_ENABLED` (padrão false), `APP_FOTO_MAX_ARQUIVO` (5MB), `APP_FOTO_MAX_REQUISICAO` (6MB), `APP_FOTO_ENVIOS_SIMULTANEOS` (2), `APP_FOTO_PERFIL_MAX` (512KB). Foto de perfil: toda escrita passa por `FotoPerfil.normalizar` (vazia/null não altera). Fotos: bytes só em consulta própria, nunca em lista (FotoRepository.METADADOS não tem conteudo). Rota nova: incluir na matriz e documentar no Swagger (@Tag no controller; @Operation nas rotas principais).
