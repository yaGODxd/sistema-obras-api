# Proposta: foto de perfil

Esta é só a proposta da foto de perfil. Não mexi em nenhum arquivo, não rodei SQL e não fiz commit.

## 1. Onde a API grava `foto_perfil` hoje
- **`PUT /usuarios/{login}/perfil`:** chama `usuarioRepository.atualizarFoto` sempre que o campo não é null (`UsuarioService.kt:111`). Hoje uma foto `""` é gravada e apaga a foto.
- **`PUT /usuarios/{login}`:** grava pelo `COALESCE(:fotoPerfil, foto_perfil)` em `atualizarUsuarioCompleto` (`UsuarioService.kt:132`). Aqui uma foto `""` também é gravada.
- **Cadastro (`POST` de usuário):** não grava foto. O `CriarUsuarioRequest` não tem `fotoPerfil` e o `inserirUsuario` não tem essa coluna. Se o app mandar o campo, o Jackson ignora. Proponho **não mexer** no cadastro. Se vocês quiserem foto no cadastro, isso vira um campo novo que passa pela mesma normalização.

As duas rotas `PUT` têm `@StatusErroFixo(400)`. Então o 400 já é o status de contrato delas, e o `{"erro","codigo"}` passa normalmente (o `TratadorDeErros` mantém o `codigo` com status fixo). **Não muda nenhum status.**

## 2. Normalização (um ponto só, chamado nas duas rotas)
Proponho um `object FotoPerfil` no pacote `service`, com `fun normalizar(texto: String?): String?`:

1. **Null ou só espaços** → `null`. Quem chama não altera a foto.
2. **Limpeza:** remove todo espaço em branco (`\s`), inclusive `\n` e `\r`.
3. **Prefixo:**
   - se começa com `data:image/jpeg;base64,`, `data:image/jpg;base64,` ou `data:image/png;base64,`, separa o prefixo do base64;
   - se começa com `data:` de outro tipo (gif, webp, pdf…), responde `FOTO_PERFIL_INVALIDA`;
   - se não tem prefixo, o texto inteiro é o base64.
4. **Leitura do base64:** decodifica com `Base64.getMimeDecoder()`, que tolera o formato dos celulares antigos. Se falhar, responde `FOTO_PERFIL_INVALIDA`.
5. **Tamanho:** se os bytes decodificados passam do teto, responde `FOTO_PERFIL_GRANDE_DEMAIS`.
6. **Tipo:** vem dos **bytes**, com o mesmo `FotoService.tipoMime` da Parte 9 (JPEG `FF D8 FF`, PNG `89 50 4E 47…`). Isso é o mesmo que olhar `/9j/` e `iVBOR` no texto, mas também pega um texto qualquer que por acaso comece com essas letras. Se não for JPEG nem PNG, responde `FOTO_PERFIL_INVALIDA`.
7. **Resultado:** sempre o formato padrão `data:image/jpeg;base64,<limpo>` ou `data:image/png;base64,<limpo>`. Se o prefixo enviado disser png mas a imagem for jpeg, o prefixo é corrigido. `image/jpg` vira `image/jpeg`.

**Erros:** os dois são 400 com códigos novos em `CodigosErro`.

| Código | Mensagem |
|---|---|
| `FOTO_PERFIL_INVALIDA` | "A foto de perfil precisa ser uma imagem JPEG ou PNG." |
| `FOTO_PERFIL_GRANDE_DEMAIS` | "A foto de perfil passou do tamanho máximo (512 KB)." |

**Nas duas rotas:**
- `atualizarPerfil`: `FotoPerfil.normalizar(request.fotoPerfil)?.let { atualizarFoto(login, it) }`.
- `atualizarUsuario`: passa `fotoPerfil = FotoPerfil.normalizar(request.fotoPerfil)` (null mantém a foto atual pelo `COALESCE`).

A validação roda **antes** de qualquer gravação. Assim, um erro na foto não deixa o nome ou outro dado gravado pela metade.

⚠️ **Decisão sua:** hoje `""` apaga a foto. Pela regra pedida ("vazia não altera"), **deixa de existir um jeito de remover a foto**. Se precisarem remover, sugiro uma rota própria depois (por exemplo `DELETE /usuarios/{login}/foto`), em vez de usar a string vazia para isso.

## 3. Teto de tamanho: proponho **512 KB da imagem decodificada**
- Em base64 isso dá uns 700 mil caracteres. O teto vale sobre os bytes, não sobre o texto, para o prefixo e as quebras de linha não contarem.
- **Motivo:** a foto de perfil viaja junto em **listas**: `GET /usuarios` (que carrega a entidade inteira), motoristas online, tela de logs e o resumo do veículo. Com 30 usuários a 512 KB, `GET /usuarios` já passa de 20 MB. Mais que isso pesa no front.
- **Recomendação para o app:** o app novo deveria mandar a foto reduzida para 256 a 512 px, JPEG 80, o que dá uns 30 a 80 KB.
- **Risco:** não sei o tamanho que os **celulares antigos** mandam. A coluna recusa tudo hoje, então não há dado no banco para medir. Se mandarem a foto da câmera inteira (2 a 4 MB), vão receber 400.
  - Por isso proponho deixar o teto numa variável, `APP_FOTO_PERFIL_MAX`, com padrão `512KB`.
  - Se aparecer recusa de celular antigo, sobe no Render sem novo deploy.
  - É variável nova, e só entra com seu OK.
- **Tamanho da requisição:** não esbarra em nenhum limite do servidor. O Jackson aceita textos bem maiores que isso no JSON.

## 4. `columnDefinition = "text"` na entidade
**Não é necessário para funcionar.** Com `ddl-auto=none` o Hibernate não cria nem confere a coluna. Todas as gravações são consultas nativas, e na leitura o `String` não tem limite. O `length = 255` padrão do `@Column` só valeria se o Hibernate gerasse o esquema.

Proponho **pôr mesmo assim**: `@Column(name = "foto_perfil", columnDefinition = "text")`. Não muda o comportamento, documenta o tipo real e evita que alguém volte a coluna para `varchar(255)` num `ddl-auto=update`.

## 5. Testes (sem banco, como sempre)
- **`FotoPerfilTest` (unitário puro):**
  - JPEG em base64 com `\n` e espaços → sai com `data:image/jpeg;base64,` e sem quebras;
  - PNG sem prefixo → recebe `data:image/png;base64,`;
  - já com prefixo `data:image/jpeg` → fica igual;
  - `data:image/jpg` → vira `jpeg`;
  - prefixo png com bytes de jpeg → prefixo corrigido;
  - GIF, PDF ou `data:image/webp` → `FOTO_PERFIL_INVALIDA`;
  - base64 quebrado → `FOTO_PERFIL_INVALIDA`;
  - texto `"/9j/abc"` que não decodifica numa imagem → `FOTO_PERFIL_INVALIDA`;
  - 512 KB + 1 byte → `FOTO_PERFIL_GRANDE_DEMAIS`, e 512 KB exatos passam;
  - `null`, `""` e `"  \n "` → `null`.
- **`UsuarioServiceFotoTest` (com mocks):**
  - `atualizarPerfil` com `""` não chama `atualizarFoto`;
  - com base64 cru, chama `atualizarFoto` com o texto já normalizado;
  - com foto inválida, lança o erro e não grava nada (nem o nome);
  - em `atualizarUsuario`, foto vazia chega `null` no `atualizarUsuarioCompleto`.
- **`ErrosDasRotasTest`:**
  - o teste atual "foto de perfil nao tem limite de tamanho na API" continua passando, porque o service é mock. Mas o nome fica enganoso, então proponho trocar para "foto de perfil grande nao esbarra em limite do JSON";
  - novo teste: quando o service lança `FOTO_PERFIL_INVALIDA`, `PUT /usuarios/joao/perfil` e `PUT /usuarios/joao` respondem 400 com `{"erro","codigo"}`.

## 6. Ordem em produção
1. Você roda o `ALTER TABLE usuarios ALTER COLUMN foto_perfil TYPE text`. É compatível com o código atual, que continua igual.
2. Depois sobe o código.

Não entra outro SQL e não há backfill: não existe foto gravada hoje.

## 7. Achado
O `GET /usuarios` usa `findAll()`, então vai devolver a foto de todo mundo. Quando as fotos começarem a existir, vale um campo novo, uma rota de foto por login, ou o front pedir a lista sem fotos. Fica para as pendências e não entra nesta parte.

**Preciso do seu OK em três pontos:**
1. O teto de 512 KB, com ou sem a variável `APP_FOTO_PERFIL_MAX`.
2. O fim do "`""` apaga a foto".
3. O cadastro fica de fora.
