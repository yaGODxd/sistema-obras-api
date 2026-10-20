# Operação do banco (Render) — passo a passo

Escrito para Windows + PowerShell. **Nunca cole senha ou URL do banco em chat, commit ou arquivo do projeto.**

## 0. Prazo

O banco gratuito do Render expira (previsão anotada: **~16/11/2026**). Faça o backup **antes** disso, com folga.
Depois que expira, os dados podem ser apagados.

## 1. Instalar as ferramentas do PostgreSQL (uma vez)

1. Descubra a versão do banco: no seu cliente SQL, rode `SELECT version();` (ex.: `PostgreSQL 16.x`).
2. Instale as ferramentas de linha de comando do PostgreSQL **da mesma versão ou mais nova**
   (site oficial postgresql.org → Download → Windows; no instalador basta marcar "Command Line Tools").
3. Abra um PowerShell novo e confira: `pg_dump --version`.
   Se não achar o comando, use o caminho completo, ex.: `& "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe" --version`.

## 2. Pegar os dados de conexão

No painel do Render → seu banco → **Connections**: copie *Hostname* (o **externo**), *Port*, *Database*, *Username* e *Password*.
Use só na sua máquina, no PowerShell, sem salvar em arquivo.

## 3. Fazer o backup

```powershell
New-Item -ItemType Directory -Force "C:\Users\yagom\backups-sgfe" | Out-Null

# Senha e SSL só nesta janela do PowerShell (somem ao fechar).
$env:PGPASSWORD = Read-Host "Senha do banco"
$env:PGSSLMODE = "require"

$data = Get-Date -Format "yyyy-MM-dd"

# Formato do PostgreSQL (para restaurar com pg_restore)
pg_dump --host=HOST_EXTERNO --port=5432 --username=USUARIO --dbname=BANCO `
        --no-owner --no-privileges --format=custom `
        --file="C:\Users\yagom\backups-sgfe\sgfe-$data.dump"

# Cópia em SQL puro (dá para abrir no bloco de notas)
pg_dump --host=HOST_EXTERNO --port=5432 --username=USUARIO --dbname=BANCO `
        --no-owner --no-privileges --format=plain `
        --file="C:\Users\yagom\backups-sgfe\sgfe-$data.sql"

Remove-Item Env:PGPASSWORD
```

Troque `HOST_EXTERNO`, `USUARIO` e `BANCO` pelos dados do passo 2.

### Conferir o backup

```powershell
pg_restore --list "C:\Users\yagom\backups-sgfe\sgfe-$data.dump" | Select-Object -First 30
Get-Item "C:\Users\yagom\backups-sgfe\*" | Select-Object Name, Length
```

Devem aparecer as tabelas (`diarios_bordo`, `abastecimentos`, `usuarios`...), os enums e as funções
`fn_abre_diario` / `fn_fecha_diario`. Guarde os arquivos também fora do computador (ex.: Google Drive).
O backup tem dados pessoais (nomes, CPF, fotos): **não** coloque no repositório.

## 4. Trocar a senha do banco (a atual foi exposta)

O caminho mais simples, já que o banco gratuito vai expirar de qualquer jeito, é **mudar para um banco novo**
(seção 5): o banco novo vem com credenciais novas e a senha exposta deixa de valer quando o antigo for apagado.

Se for continuar no mesmo banco:
1. No painel do Render, veja se o banco tem opção de trocar/rotacionar a senha ou as credenciais.
   Se tiver, use-a e anote a nova senha só no Render (passo 3 da seção 5).
2. Se não tiver, me avise antes de tentar outra forma (trocar a senha por SQL deixa os dados mostrados no painel
   desatualizados e pode confundir).

## 5. Mudar a API para outro banco (novo, ou com senha nova)

1. Crie o banco novo no Render (mesma região do serviço da API) e espere ficar disponível.
2. Restaure o backup nele:
   ```powershell
   $env:PGPASSWORD = Read-Host "Senha do banco NOVO"
   $env:PGSSLMODE = "require"
   pg_restore --host=HOST_NOVO --port=5432 --username=USUARIO_NOVO --dbname=BANCO_NOVO `
              --no-owner --no-privileges "C:\Users\yagom\backups-sgfe\sgfe-AAAA-MM-DD.dump"
   Remove-Item Env:PGPASSWORD
   ```
   Avisos sobre "owner" ou "extension" podem aparecer; confira depois com
   `SELECT COUNT(*) FROM diarios_bordo;` no banco novo e compare com o antigo.
3. No Render → serviço da API → **Environment**, atualize:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://HOST_INTERNO:5432/BANCO_NOVO?sslmode=require`
     (o Render mostra a URL como `postgresql://...`; para o Spring é `jdbc:postgresql://host:porta/banco`, **sem** usuário e senha na URL)
   - `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` com os dados novos.
   Salve: a API reinicia sozinha.
4. Teste: login no front, abrir e fechar um diário de teste, ver `/logs`.
5. Só depois de alguns dias sem problema, apague o banco antigo.

## 6. Se algo der errado

- A API não sobe depois de trocar as variáveis: volte as variáveis antigas no Render (o banco antigo continua lá até você apagar).
- Erro de SSL: confira o `?sslmode=require` no fim da URL.
- `pg_dump` reclamando de versão: instale as ferramentas da mesma versão do servidor ou mais nova.
