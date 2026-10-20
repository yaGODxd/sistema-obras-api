# Proposta: o Render compilar a API (Dockerfile multi-stage) — NÃO APLICADA

Hoje o `Dockerfile` só copia o jar que está no repositório (`COPY build/libs/*.jar app.jar`).
Por isso o jar é versionado e todo commit precisa levar o jar novo. Esta proposta faz o Render compilar sozinho.
**Nada aqui foi aplicado.** Só executar com aprovação.

## Dockerfile proposto

```dockerfile
# Etapa 1: compila (tem JDK e o código)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# Etapa 2: só o necessário para rodar (imagem menor)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/sistemaobras-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

E um `.dockerignore`:

```
build/
.gradle/
.idea/
*.iml
docs/
```

## O que mudaria junto (quando aprovado)

1. Trocar o `Dockerfile` e criar o `.dockerignore`.
2. No `build.gradle.kts`, desligar o `-plain.jar` (`tasks.named("jar") { enabled = false }`), para sobrar um jar só.
3. Tirar o jar do git (`git rm --cached build/libs/*.jar`) e tirar a exceção `!build/libs/*.jar` do `.gitignore`.
4. Atualizar `README.md` e `CLAUDE.md` (não precisa mais commitar o jar).

## Riscos

- **Tempo e memória no plano gratuito**: o Gradle baixa dependências e compila no Render; o deploy fica mais lento
  (minutos) e pode faltar memória. Se faltar, voltar para o Dockerfile atual.
- **`gradlew` sem permissão**: commits feitos no Windows perdem a permissão de execução; o `chmod +x` resolve.
- **Testes**: o `-x test` pula os testes no Render (eles continuam rodando na sua máquina antes do push).
  Rodar os testes no Render deixaria o deploy ainda mais lento.
- **Erro de compilação vai direto para o deploy**: hoje um erro aparece quando você gera o jar; com a proposta,
  aparece no log do Render e o deploy falha (a versão anterior continua no ar).

## Como testar antes de trocar

Num branch separado: aplicar as mudanças, fazer push do branch, criar um serviço de teste no Render apontando para
esse branch (sem mexer no serviço principal) e ver se o build e a aplicação sobem.

## Como desfazer

Voltar o `Dockerfile` antigo, recolocar a exceção no `.gitignore`, gerar o jar e commitá-lo.
