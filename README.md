# SGFE — API

API do Sistema de Gestão de Frota (SGFE). Atende o front Angular (gestor) e o app Android (motorista).
Kotlin + Spring Boot 4.1, Java 17, PostgreSQL (Render).

## Rodar os testes e gerar o jar (Windows / PowerShell)

```powershell
.\gradlew.bat test       # testes (não usam banco: só mocks e @WebMvcTest)
.\gradlew.bat bootJar    # gera build\libs\sistemaobras-0.0.1-SNAPSHOT.jar
```

O jar em `build/libs/` **é versionado de propósito**: o `Dockerfile` copia esse jar. Depois de mudar o código,
gere o jar e faça commit dele junto. Não commite o `-plain.jar`.

## Rodar a aplicação no computador

Só com um PostgreSQL **local** (nunca apontando para o banco do Render):

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/sgfe"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "<senha do banco local>"
$env:JWT_SECRET = "<qualquer texto longo, só para teste>"
.\gradlew.bat bootRun
```

O esquema do banco é manual (`ddl-auto=none`): o banco local precisa ter as mesmas tabelas do Render
(restaure um backup, ver `docs/OPERACAO.md`).

## Variáveis de ambiente

| Variável | Padrão | Para que serve |
|---|---|---|
| `SPRING_DATASOURCE_URL` | — | URL JDBC do banco (`jdbc:postgresql://host:5432/banco?sslmode=require`) |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | — | Usuário e senha do banco |
| `JWT_SECRET` | — | Chave que assina os tokens (trocar invalida todos os logins) |
| `JWT_EXPIRATION` | `86400000` | Validade do token em ms (24 h) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `none` | Deixe `none`: o esquema é manual |
| `SPRING_JPA_SHOW_SQL` | `false` | `true` mostra o SQL no log (só para diagnóstico) |
| `APP_SEGURANCA_EXIGIR_JWT` | `false` | `true` liga a matriz de permissões (rota × perfil) |
| `APP_CORS_ORIGENS` | `*` | Origens do front, separadas por vírgula |
| `APP_BLOQUEIOS_DIARIO` | `true` | `false` desliga os bloqueios de PIN, diário pendente e sair de serviço |
| `APP_VALIDAR_CPF_DIGITOS` | `false` | `true` confere os dígitos do CPF no cadastro |
| `APP_DATAS_COM_FUSO` | `false` | `true` faz as datas das respostas saírem com `Z` (UTC) |
| `SWAGGER_ENABLED` | `false` | `true` liga a documentação em `/swagger-ui.html` |
| `APP_FOTO_MAX_ARQUIVO` / `APP_FOTO_MAX_REQUISICAO` | `5MB` / `6MB` | Tamanho máximo da foto e do envio inteiro |
| `APP_FOTO_ENVIOS_SIMULTANEOS` | `2` | Quantas fotos podem ser gravadas ao mesmo tempo (memória) |

Trocar uma variável no Render reinicia a aplicação; não precisa gerar jar novo.

## Ordem para publicar uma mudança

1. **SQL primeiro**: se a parte tiver SQL em `docs/migracoes.sql`, rode antes do deploy
   (os blocos são feitos para funcionar com o código antigo ainda no ar).
2. `.\gradlew.bat test` e `.\gradlew.bat bootJar`.
3. Commit (com o jar) e push. O Render publica sozinho.
4. Espere subir (o primeiro acesso demora cerca de 1 minuto) e teste.

## Documentação

- Swagger (com `SWAGGER_ENABLED=true`): `/swagger-ui.html` — rotas, exemplos, erros e códigos. Botão **Authorize** para o token.
- `docs/PLANO-API.md` — plano de trabalho e decisões. `docs/PENDENCIAS-API.md` — pendências.
- `docs/migracoes.sql` — todo SQL do projeto. `docs/para-revisao.txt` — blocos para revisão.
- `docs/OPERACAO.md` — backup do banco, troca de senha, mudança de banco.
- `docs/PROPOSTA-DOCKERFILE.md` — proposta (não aplicada) de compilar no Render.

## Erros

Toda resposta de erro tem o corpo `{"erro": "mensagem"}` e, em alguns casos, `"codigo"` (estável, para o app decidir
o que fazer). Rotas antigas mantêm o status de erro de antes por compatibilidade. A tabela completa está no Swagger.
