# API SGFE quase completa — panorama para o time do app (19/10/2026)

Este relatório resume **tudo o que mudou na API** e **o que o app Android precisa fazer** para acompanhar.
Foi escrito para o Claude Code do app usar como base das correções. O contrato detalhado (todas as rotas, campos
e exemplos) está no Swagger: `https://<endereço da API no Render>/swagger-ui.html` (ligado com `SWAGGER_ENABLED=true`).

> Regra de ouro da API: **só foram ACRESCENTADOS campos e rotas**. Nada foi renomeado ou removido, e o status das
> respostas de sucesso não mudou. Um app que não manda os campos novos continua funcionando como antes
> (com as exceções de validação e bloqueio listadas na seção 4).

---

## 1. Visão geral

- Kotlin + Spring Boot 4.1, PostgreSQL no Render. Datas guardadas em **UTC**.
- Login: `POST /auth/login` → `{token, nome, perfil}`. Token JWT com `{sub, perfil, iat, exp}` (`exp` em **milissegundos**, 24 h). Não mudou.
- Toda resposta de erro: `{"erro": "mensagem em português"}` e, em vários casos, `"codigo"` (texto estável para o app decidir o que fazer).
- Interruptores no Render (mudam sem novo deploy):

| Variável | Padrão | Efeito para o app |
|---|---|---|
| `APP_SEGURANCA_EXIGIR_JWT` | `false` | `true` exige token em TODAS as rotas (só `POST /auth/login` fica público) |
| `APP_BLOQUEIOS_DIARIO` | `true` | Bloqueios de PIN, diário pendente e sair de serviço (seção 4) |
| `APP_DATAS_COM_FUSO` | `false` | `true` faz as datas das respostas saírem com `Z` (seção 6) |
| `APP_VALIDAR_CPF_DIGITOS` | `false` | Confere dígitos do CPF no cadastro |
| `SWAGGER_ENABLED` | `false` | Liga a documentação |

---

## 2. Erros: formato, status e quando tentar de novo

Corpo: `{"erro": "...", "codigo": "..."}` (`codigo` só aparece em alguns erros).

**Rotas antigas** (abrir/fechar diário por login, turnos, abastecimento, ocorrência, rastreamento ponto a ponto)
mantêm o status de erro de sempre (**400** em quase tudo) por compatibilidade. **Use o `codigo` para decidir**, não o status.

**Rotas novas** usam status coerentes:

| Status | Significado | A fila do app deve... |
|---|---|---|
| 200 | gravou, ou é reenvio de algo já gravado | remover da fila |
| 400 | dado inválido / regra violada | marcar erro permanente (mostrar ao motorista) |
| 401 | sem token ou token vencido | pedir login de novo e depois reenviar |
| 403 | sem permissão / diário de outro motorista | erro permanente |
| 404 | não encontrado | erro permanente |
| 409 | conflito de estado | ver o `codigo` (abaixo) |
| 422 | horário mais de 24 h no futuro | erro permanente (relógio do celular errado) |
| 5xx / sem rede | falha do servidor | **tentar de novo** depois |

### Códigos

| Código | Onde | O app deve... |
|---|---|---|
| `PIN_AUSENTE` | abrir diário | **deixar a fila em espera** e levar o motorista a cadastrar o PIN |
| `DIARIO_PENDENTE_ASSINATURA` | abrir diário | **deixar a fila em espera** e levar o motorista a assinar o diário pendente |
| `TURNO_COM_DIARIO_ABERTO` | sair de serviço | **deixar em espera**: fechar o diário antes |
| `SEM_DIARIO_ABERTO` | abastecimento/ocorrência sem `diarioId`, fechar por login | mandar `diarioId` (seção 5) |
| `DIARIO_NAO_ENCONTRADO` | eventos com `diarioId` | erro permanente |
| `DIARIO_DE_OUTRO_MOTORISTA` | eventos com `diarioId` | erro permanente |
| `DIARIO_ASSINADO` | evento para diário já assinado | erro permanente |
| `DIARIO_JA_FECHADO` | fechar diário que já foi fechado por outro envio | erro permanente (conferir o diário) |
| `ID_LOCAL_EM_USO` | `idLocal` já usado por outro motorista | gerar outro `idLocal` (bug do app) |
| `REENVIO_SIMULTANEO` | dois envios iguais ao mesmo tempo | **tentar de novo** (o reenvio devolve o registro gravado) |
| `HORARIO_INVALIDO` | `ocorridoEm`/`enviadoEm` sem fuso | corrigir o formato (bug do app) |
| `HORARIO_FUTURO` | horário mais de 24 h no futuro | erro permanente |
| `LOTE_GRANDE_DEMAIS` | lote com mais de 200 pontos | dividir o lote |
| `ID_INVALIDO` | id que não é UUID | bug do app |
| `STATUS_INVALIDO` | filtro de status de ocorrência | bug |

Mensagens úteis que **não têm código** (status 400 na rota antiga): "Motorista não tem turno aberto",
"Motorista já tem um diário aberto", "O medidor final não pode ser menor que o medidor inicial.",
"Já existe um turno aberto para este usuário.", "Não há turno aberto para este usuário.".

---

## 3. Segurança e identidade

- **Mandar `Authorization: Bearer <token>` em TODAS as chamadas.** Hoje a maioria das rotas ainda aceita sem token,
  mas o Yago vai ligar `APP_SEGURANCA_EXIGIR_JWT=true` quando o app estiver pronto. A partir daí, chamada sem token = 401.
- Com token de motorista, **a API usa o login do token**, não o `loginMotorista`/`login` do corpo.
- Motorista só acessa os próprios dados: rotas com `{login}` de outra pessoa ou diários de outro motorista → 403
  (400 nas rotas antigas).
- Rotas do motorista (com a matriz ligada): turnos (abrir, fechar, status), diários (abrir, fechar, `/meus`, editar,
  assinar, verificar, detalhe do próprio), abastecimentos, ocorrências, rastreamento (ponto e lote), respostas do
  checklist, PIN (`/usuarios/me/pin`), perfil próprio, veículos (consultas), postos, checklist (consultas).
  Mecânicos, manutenções, logs e cadastros são só do gestor.

---

## 4. Validações e bloqueios novos (podem recusar o que antes era aceito)

- Abastecimento: `litros` **maior que 0**; `valorTotal` pode ser 0 (comboio); `tipoCombustivel` obrigatório.
- Ocorrência: `tipo` e `descricao` obrigatórios.
- Medidor: não pode ser negativo; **medidor final não pode ser menor que o inicial**.
- Textos: no máximo 255 caracteres (mensagem: "Campo 'x' deve ter no máximo 255 caracteres.").
- **Abrir diário** exige PIN cadastrado (`PIN_AUSENTE`) e nenhum diário fechado pendente de assinatura (`DIARIO_PENDENTE_ASSINATURA`).
- **Sair de serviço** com diário aberto é recusado (`TURNO_COM_DIARIO_ABERTO`). Diário só pendente de assinatura não impede.
- Esses três bloqueios podem ser desligados no Render (`APP_BLOQUEIOS_DIARIO=false`) se algum motorista ficar travado.

Mensagens de validação seguem o padrão: `{"erro": "Campo 'litros' deve ser maior que zero."}`.

---

## 5. Uso offline (o mais importante para o app)

A API agora aceita eventos que chegam **depois**, com a **hora real** em que aconteceram.

### Campos opcionais novos (em turno abrir/fechar, diário abrir/fechar, abastecimento, ocorrência, rastreamento)

| Campo | Formato | Para quê |
|---|---|---|
| `ocorridoEm` | ISO-8601 **com fuso**, ex.: `"2026-10-19T12:00:00Z"` ou `"2026-10-19T09:00:00-03:00"` | hora real do evento (capturar no toque do botão, não no envio) |
| `idLocal` | UUID gerado no celular, um por evento | reenvio do mesmo evento **não duplica**: a API devolve o já gravado (200) |
| `enviadoEm` | ISO-8601 com fuso, no momento do envio | a API mede o desvio do relógio do celular |
| `diarioId` | UUID devolvido ao abrir o diário | liga o evento ao diário certo mesmo se ele já foi fechado |

- Sem `ocorridoEm`, vale o `registradoEm` antigo (número em milissegundos); sem os dois, a hora do servidor.
- `registradoEm` (ms) é tratado como instante absoluto (UTC) desde 19/10. Antes ele era convertido errado (3 h de diferença).
- Horário estranho **não é recusado**, só marcado (`horarioSuspeito: true` e `motivoSuspeita`): até 24 h no futuro,
  mais de 7 dias atrás, relógio desviado mais de 5 min, fora do período do diário. Acima de 24 h no futuro → 422.
- Diário **fechado e ainda não assinado** aceita eventos atrasados. Diário **assinado** recusa (`DIARIO_ASSINADO`).
- `idLocal` precisa ser gerado **uma vez** e guardado com o evento na fila; reenvios usam o mesmo valor.

### Rotas novas

**Fechar diário pelo id**: `POST /diarios/{id}/fechar`
```json
{ "medidorFinal": 12620.0, "observacaoFechamento": "Sem problemas",
  "ocorridoEm": "2026-10-19T17:40:00Z", "idLocal": "8c3c...", "enviadoEm": "2026-10-19T18:10:00Z" }
```
200 com o diário; 409 `DIARIO_JA_FECHADO` se outro envio já fechou; mesmo `idLocal` → 200 com o resultado gravado.
A rota antiga `POST /diarios/fechar/{login}` continua e aceita os mesmos campos.

**Rastreamento em lote**: `POST /rastreamento/lote` (no máximo **200** pontos)
```json
{ "diarioId": "3fa8...", "enviadoEm": "2026-10-19T18:10:00Z",
  "pontos": [ { "latitude": -21.1, "longitude": -47.8, "velocidade": 40.0,
                "ocorridoEm": "2026-10-19T15:00:00Z", "idLocal": "a1b2..." } ] }
```
Resposta 200:
```json
{ "diarioId": "3fa8...", "recebidos": 1, "gravados": 1, "duplicados": 0, "descartados": 0, "recusados": 0,
  "pontos": [ { "indice": 0, "idLocal": "a1b2...", "resultado": "gravado" } ] }
```
`resultado`: `gravado` | `duplicado` (já recebido) | `descartado_proximo` (a menos de 50 m e 2 min de outro ponto)
| `recusado` (com `codigo` e `erro`). Só os `recusado` com erro permanente devem virar erro no app; os outros saem da fila.

### Outros comportamentos

- **Abastecimento no comboio offline**: com `veiculoAbastecidoId`, a API acha o diário do veículo abastecido **no horário
  do evento** (aceita diário já fechado, não assinado). A API guarda quem registrou (usuário do token).
- **Checklist**: `POST /checklist/respostas` aceita `ocorridoEm`/`enviadoEm`. Reenviar não dá mais erro: atualiza a
  resposta, e um reenvio mais antigo não desfaz uma resposta mais nova. Sem `idLocal` aqui.
- **Ponto de rastreamento sem diário aberto** (sem `diarioId`): continua sendo ignorado em silêncio (200).
- **Abrir diário offline**: parar de escrever "Diário aberto em modo offline em ..." na observação; mandar `ocorridoEm`.

---

## 6. Datas nas respostas

- Hoje as datas saem **sem fuso**, mas são **UTC**: `"2026-10-19T12:00:00"` = 12:00 UTC = 09:00 em Brasília.
  **O app deve tratar como UTC e converter para o fuso do celular na tela.** Se hoje o app mostra a hora "crua",
  ela está 3 h adiantada.
- Quando o app estiver lendo datas como instante (`Instant`/`OffsetDateTime`, aceitando `Z`), o Yago liga
  `APP_DATAS_COM_FUSO=true` e as datas passam a sair como `"2026-10-19T12:00:00Z"`.
  **`LocalDateTime.parse` quebra com o `Z`**: ajustar o app antes de ligar.
- Filtros de dia/mês (logs, diários por mês) já são cortados no fuso de Brasília.

---

## 7. Campos novos nas respostas (só acréscimos)

| Resposta | Campos novos |
|---|---|
| Diário (`DiarioResponse`, listas, abrir, fechar) | `veiculoId`, `tipoMedidor` (`hodometro` ou `horimetro`) |
| `GET /diarios/{id}` (rota nova, detalhe) | + `motivoUso`, `motivoUsoId`, `observacaoFechamento`, `assinadoEm`, `motoristaLogin` |
| Abastecimento | `comboio`, `veiculoAbastecidoId`, `recebidoEm`, `idLocal`, `horarioSuspeito`, `motivoSuspeita` (e `id` agora vem preenchido no registro) |
| Ocorrência | `status` (`pendente`, `em_andamento`, `resolvida`), `resolvidaEm`, `recebidoEm`, `idLocal`, `horarioSuspeito`, `motivoSuspeita` |
| Rastreamento (pontos do diário) | `recebidoEm`, `idLocal`, `horarioSuspeito`, `motivoSuspeita` |
| Veículo | `tipoId`, e `tipoNome`/`tipoMedidor` agora vêm preenchidos nas listas |

Status da ocorrência: nasce `pendente`; vira `em_andamento` quando o gestor abre manutenção ligada a ela e
`resolvida` quando todas as manutenções ligadas terminam (ou quando o gestor muda à mão).

---

## 8. Lista de tarefas sugerida para o app (em ordem)

1. Mandar o token em **todas** as chamadas (pré-requisito para o Yago ligar `APP_SEGURANCA_EXIGIR_JWT`).
2. Fluxo de PIN obrigatório e de assinar diário pendente; tratar `PIN_AUSENTE`, `DIARIO_PENDENTE_ASSINATURA` e
   `TURNO_COM_DIARIO_ABERTO` deixando a fila em espera e levando o motorista à tela certa.
3. Validar antes de enviar: litros > 0, textos obrigatórios, medidor final ≥ inicial, até 255 caracteres.
4. Fila offline: capturar `ocorridoEm` no momento da ação, gerar `idLocal` por evento (guardar na fila),
   mandar `enviadoEm` e `diarioId`; usar `POST /diarios/{id}/fechar` e `POST /rastreamento/lote` (até 200 por chamada).
5. Política de reenvio pela tabela da seção 2 (5xx e `REENVIO_SIMULTANEO`: tentar de novo; demais 4xx: erro).
6. Tratar datas recebidas como UTC; depois migrar a leitura para `Instant`/`OffsetDateTime` (seção 6).
7. Mostrar o `status` da ocorrência nos detalhes do diário.
8. Tirar o texto "Diário aberto em modo offline" da observação.

**Ainda NÃO prontos na API** (não implementar no app ainda): fotos de comprovação (hodômetro, cupom, bomba) — item 14,
depende de decidir onde guardar as fotos.

---

## 8b. Novidades depois deste relatório: motivos de uso e fotos (Parte 9)

**Motivos de uso**: `GET /motivos-uso` → `[{id, descricao}]` (só ativos). Trocar o campo de texto "motivo" da
abertura do diário por uma lista e mandar o `id` escolhido em `motivoUsoId` do `POST /diarios/abrir`.

**Fotos de comprovação** (só depois que o SQL da Parte 9 rodar no banco e o código for publicado):
- Envio separado do evento: primeiro grava o evento (abastecimento/ocorrência) e guarda o `id` devolvido;
  depois `POST /fotos` em `multipart/form-data` com `arquivo` (JPEG ou PNG, até 5 MB; reduzir para 1920 px, JPEG 80),
  `tipo`, `idLocal` (próprio da foto), `ocorridoEm` (hora em que tirou), `enviadoEm` e o vínculo:
  - `medidor_abertura` / `medidor_fechamento`: `diarioId`;
  - `ocorrencia`: `diarioId` + `ocorrenciaId`;
  - `cupom_fiscal` / `bomba_comboio`: `abastecimentoId` (o diário vem do abastecimento; no comboio, quem registrou pode enviar);
  - `livre`: `diarioId`.
- Mesmo `idLocal` → 200 com a foto já gravada. Erros: 413 `FOTO_GRANDE_DEMAIS` e 415 `TIPO_ARQUIVO_INVALIDO` (permanentes),
  400 `VINCULO_INVALIDO`/`TIPO_FOTO_INVALIDO`, 409 `DIARIO_ASSINADO`, 503 `FOTO_OCUPADO` (tentar de novo).
- Mandar as fotos ANTES de assinar o diário (diário assinado recusa foto).
- Leitura: `GET /diarios/{id}/fotos` (lista sem os bytes) e `GET /fotos/{id}` (a imagem, com cache e ETag).

## 9. Situação da API (para o Yago)

Feito: erros padronizados, validações e unicidade, status da ocorrência, transações, fuso UTC, respostas mais
completas, consultas mais leves, segurança com matriz de permissões (desligada), bloqueios no servidor, uso offline,
cortes de dia/mês em Brasília, datas com fuso (desligado), Swagger, documentação de operação.

Pendências que dependem do Yago: rodar o SQL 1 da 6B no banco (obrigatório para o uso offline funcionar), revisão dos
triggers novos (SQL 2), correção dos horários antigos (6A, PASSO 1 e 2), backfill das ocorrências, backup do banco antes
de ~16/11/2026, ligar as variáveis quando o app estiver pronto. Detalhes: `docs/PENDENCIAS-API.md` e `docs/para-revisao.txt`.
