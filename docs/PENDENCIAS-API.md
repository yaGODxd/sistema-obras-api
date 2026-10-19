# Pendências da API (SGFE) — lista viva

Atualizada em 19/10/2026. Repositório: github.com/yaGODxd/sistema-obras-api
Regra de deploy: o Dockerfile faz COPY build/libs/*.jar. Depois de mudar o código, gerar o jar de novo (bootJar) e commitar o jar. NÃO pôr o jar no .gitignore enquanto o Dockerfile for assim.

## Ordem sugerida (igual às Partes de docs/PLANO-API.md)
- Parte 1: erros padronizados (item 2) + desligar show-sql (item 17)
- Parte 2: validações e unicidade (item 3)
- Parte 3: ocorrência com status + manutenção (itens 18, 19, 16)
- Parte 4: respostas mais completas e consultas mais leves (itens 10, 13, resto do 17)
- Parte 5: rotas atrás do JWT, bloqueios no servidor, CORS (itens 8, 9, 15, 4) -- precisa conferir o app antes
- Parte 6A: fuso do registradoEm e hora do servidor em UTC (item 1, parte) -- FEITA em 19/10, ANTES da Parte 3
- Parte 6B: horário do evento capturado no celular, uso offline (itens 1, 5, 6) -- publicar a API antes do app
- Parte 7: Swagger/OpenAPI (item 11), por último, para refletir o contrato final
- Parte 8: operação (item 12): backup, senha do banco, README, Dockerfile multi-stage (só proposta)
- Itens 7 e 14 (fotos) ficam fora deste ciclo: 7 é condicional ao tamanho; 14 espera o app.

## Itens

1. Horário do evento (uso offline)
   - FEITO (Parte 6A, 19/10, falta deploy): registradoEm convertido com UTC; hora do servidor em UTC explícito (código, fuso da aplicação e sessão do banco). Registros antigos: pré-visualização e correção (+3 h, só com autorização) no bloco "Parte 6A" de docs/migracoes.sql. Abastecimentos de diário assinado não são corrigidos (entram no resumo da assinatura).
   - ACHADO (18/10, lendo o código): AbastecimentoRequest e OcorrenciaRequest JÁ aceitam `registradoEm` (Long, epoch em milissegundos). O service converte com ZoneId America/Sao_Paulo e grava hora LOCAL de Brasília. Sem o campo, grava LocalDateTime.now() (UTC no Render). Resultado: o banco mistura os dois fusos (registros com registradoEm = Brasília; o resto = UTC). Corrigir: converter com ZoneOffset.UTC. Registros antigos enviados com registradoEm ficam 3 h diferentes: avaliar migração.
   - Abrir diário, fechar diário e rastreamento NÃO têm campo de horário (usam o relógio do servidor, UTC).
   - DECISÃO 19/10 (Parte 6B): todo horário de evento é o capturado pelo celular no momento da ação (instante UTC). Inclui abrir/fechar TURNO e respostas de CHECKLIST, além de diário, abastecimento, ocorrência e rastreamento. A hora da ASSINATURA continua sendo a do servidor. Hoje turno (NOW() no TurnoRepository) e checklist (LocalDateTime.now() na entidade) também usam o relógio do servidor. Avaliar: hora do recebimento, enviadoEm para estimar desvio do relógio do aparelho, horário no futuro, evento fora da janela aberto_em..fechado_em do diário. Cuidado com GET /rastreamento/posicoes (filtro NOW() - 2 min).
   - ACRÉSCIMO 2 (19/10, vindo do app, Parte 6B): diarioId opcional em abastecimento, ocorrência, rastreamento e checklist (dono = motorista do token; ocorridoEm dentro de aberto_em..fechado_em, ou até agora se aberto); nova POST /diarios/{id}/fechar idempotente por idLocal (a antiga continua); POST /diarios/abrir com ocorridoEm e idLocal; nova POST /rastreamento/lote (diarioId + pontos com ocorridoEm e idLocal, aceita diário fechado dentro da janela, mantém descarte de pontos próximos, idempotente); turno abrir/fechar com ocorridoEm; status coerentes nas rotas novas para o app separar "tentar de novo" (rede/5xx) de "erro de regra" (4xx). Detalhes no PLANO, seção Parte 6B.
   - Achado 19/10: contarPontosProximos usa acos sem limitar o argumento; pontos idênticos podem gerar erro "input is out of range" no Postgres. Corrigir na 6B.
   - Verificar no app se ele realmente envia registradoEm e se captura na hora da ação ou na hora do envio.
   - Hoje o horário padrão é o now() do servidor (UTC), ou seja, a hora do sync, não a hora real.
   - Mudança: campo opcional `ocorridoEm` (ISO-8601 UTC com Z) e `idLocal` (UUID, evita duplicar em reenvio) em: abrir/fechar diário, abastecimento, ocorrência, rastreamento.
   - Gravar também `registrado_em` (hora em que o servidor recebeu). Recusar ou sinalizar horário no futuro ou muito antigo.
   - Sem o campo, continua usando now() (app antigo não quebra). Publicar a API antes do app.
   - Necessário antes de mexer: DDL real das tabelas (diários, abastecimentos, ocorrências, rastreamento) e a lista de triggers do banco. As entidades estão no repositório, mas o banco é a fonte da verdade (esquema manual): pedir ao Yago o resultado das consultas em information_schema e a lista de triggers.
   - CORRIGIDO 19/10: o código NÃO usa fn_fecha_diario. Fechar diário é UPDATE direto em diarios_bordo (calcula medidor_percorrido = medidorFinal - medidor_inicial) mais troca do veículo para disponivel. Quem atualiza veiculos.medidor_atual não aparece no código (trigger?): investigar na Parte 6.
   - CORRIGIDO 19/10: ddl-auto NÃO é none no código; o padrão é update (${SPRING_JPA_HIBERNATE_DDL_AUTO:update}). Aprovado mudar o padrão para none na Parte 1. Yago vai conferir o valor da variável no Render.
   - Ainda não se sabe se o app envia registradoEm (Yago vai perguntar ao Claude Code do app). Planejar a Parte 6 para os dois cenários. Diagnóstico (proposta, sem rodar): comparar logs.criado_em (acao ABASTECIMENTO, mesmo login) com abastecimentos.registrado_em; ~3 h de diferença = o app enviou; ~0 = não enviou.
   - Achados 19/10 (abastecimento/ocorrência):
     a) AbastecimentoService sempre calcula registradoEm em Kotlin, então o COALESCE(..., NOW()) do INSERT nunca é usado; a coluna sincronizado não é preenchida pelo INSERT (fica no default do banco).
     b) Abastecimento e ocorrência se ligam ao diário aberto AGORA (status='aberto'). Offline, o evento pode chegar depois do fechamento e falhar com "Motorista não tem diário aberto". Parte 6: aceitar diarioId opcional e validar a janela aberto_em..fechado_em.
     c) Abastecimento e ocorrência usam loginMotorista do CORPO, não do token (confirmado nos dois). Parte 5: usar o login do token.
     d) Texto do log ABASTECIMENTO com erro de precedência: o sufixo " (Comboio)" some quando há posto. Corrigir na Parte 3, etapa B.
   - Depois: prompt para o Claude Code do app (fila offline + captura do horário).
   - Registros antigos não podem ser corrigidos (limitação para o TCC).

2. Erros padronizados
   - Hoje os controllers antigos fazem try/catch de RuntimeException e devolvem 400 sem corpo (AuthController, TurnoController e outros).
   - Criar @RestControllerAdvice, exceções de negócio (regra, não encontrado, conflito) e remover os try/catch. Resposta sempre {"erro": "mensagem em português"}.
   - Login repetido: {"erro": "Já existe um usuário com este login."}.
   - Rotas que hoje podem devolver 400 sem mensagem (lista do front): POST /auth/cadastrar; PUT /usuarios/{login}, /usuarios/{login}/ativo, /usuarios/{login}/perfil; POST/PUT/DELETE /veiculos; POST/PUT /mecanicos e /mecanicos/{id}/ativo; POST/PUT /postos e /postos/{id}/ativo; POST /checklist/itens e PUT /checklist/itens/{id}/ativo; POST /manutencoes e PUT /manutencoes/{id}/status (é a única rota de alteração de manutenção).
   - O front já trata os formatos atuais (erro, Spring padrão, corpo vazio, status 0), então a troca é transparente.
   - FEITO (Parte 1, 19/10, falta deploy): pacote exception + TratadorDeErros; try/catch removidos; @StatusErroFixo mantém o status antigo de cada rota; login com mensagem única e senha conferida antes de "inativo"; 401/403 do Spring Security em {"erro"}.
   - PENDENTE (muda status, precisa de OK item a item): rotas sem status fixo hoje respondem 500 em erro (POST /mecanicos, /mecanicos/{id}/ativo, POST /postos, /postos/{id}/ativo, todo o /checklist, GETs de listagem). Propostas: id inválido 500->400; duplicado no banco 500->409 ou 400; e, nas rotas com @StatusErroFixo, "não encontrado" 400->404 e erro inesperado 400->500.

3. Validações e unicidade
   - CPF com dígitos verificadores: ADIADO de propósito (fase de testes). Ligar antes de usar de verdade, junto com o interruptor do front (validarDigitosCpf).
   - Recusar valores negativos: medidor, custos, valor, quantidade de peça. Usar Bean Validation (@PositiveOrZero etc.).
   - Confirmar se placa e CPF têm restrição de unicidade no banco; se não, criar.
   - FEITO (Parte 2, 19/10, falta deploy): Bean Validation nos DTOs de entrada (obrigatórios em branco, valores negativos, perfil gestor|motorista); API confere login, CPF (só dígitos) e placa (sem traço, ignora vazia e inativo) antes de gravar; validação de dígitos do CPF pronta e DESLIGADA (APP_VALIDAR_CPF_DIGITOS=true liga); placa vazia passa a ser gravada como null. Índices únicos só como sugestão comentada em docs/migracoes.sql.
   - FEITO (19/10, com o resultado do information_schema): limites @Size nos DTOs iguais às colunas (quase todas varchar(255); senha até 72 por causa do BCrypt). LogService corta textos do log em 255 (a descrição da ocorrência podia estourar logs.descricao DEPOIS de gravar a ocorrência).
   - FEITO (Parte 3, etapa B, 19/10, falta deploy): litros > 0, quantidade de peça >= 1, medidor final >= inicial; valor/custo/valor unitário zero aceitos.
   - DECISÕES 19/10 (para a Parte 3, etapa B): recusar litros = 0 e quantidade de peça < 1; aceitar valorTotal = 0, custo de mão de obra = 0 e valor unitário = 0 (garantia); recusar medidor final menor que o inicial ({"erro": "O medidor final não pode ser menor que o medidor inicial."}); troca de hodômetro = limitação documentada. Status de manutenção validado na Parte 3 (aberta, em_andamento, concluida). Placa de veículo inativo continua liberada para novo cadastro.
   - ACHADO 19/10 (decidir): usuarios.foto_perfil é varchar(255), mas a foto é base64 (muito maior). A API NÃO limita a foto (decisão de não reduzir foto). Diagnóstico e ALTER sugerido (comentados) no bloco 5 de docs/migracoes.sql.

4. CORS
   - FEITO (Parte 5, 19/10): origens por APP_CORS_ORIGENS (vírgula); sem a variável continua "*". Authorization já liberado (allowedHeaders "*").
   - Pendência registrada na Parte 2 do front. Recuperar o texto exato no relatório do Claude Code e conferir se Authorization está em allowedHeaders.

5. Fuso do corte de dia e de mês
   - /logs (corte do dia) e /diarios (corte do mês) podem estar cortando em UTC. Decidir: America/Sao_Paulo. Pendência registrada na Parte 3 do front.

6. Datas com fuso
   - Hoje LocalDateTime sai sem fuso (ex.: 2026-10-18T13:08:00). Passar a serializar com Z/offset de forma consistente. O interceptor do front só mexe em texto sem fuso, então é compatível.

7. Foto fora das listas (condicional)
   - A foto de perfil é base64 dentro da lista de motoristas online (recarregada a cada 15 s no dashboard). Decisão atual: não reduzir foto.
   - Se o tamanho da resposta passar de 1 a 2 MB, tirar a foto da lista e servir por rota separada com cache.

8. Fechar rotas antigas atrás do JWT (RNF10)
   - FEITO (Parte 5, 19/10): matriz rota x perfil na SecurityConfig, ligada por APP_SEGURANCA_EXIGIR_JWT=true (padrão false = comportamento antigo). Motorista só acessa os próprios dados (ControleAcesso); com token de motorista, o loginMotorista do corpo é trocado pelo do token. Ligar só depois de conferir que o app manda o token em TODAS as chamadas.
   - MATRIZ APROVADA (19/10): mecânicos e POST /auth/cadastrar só gestor. Dono do diário nas rotas por id de diário (abastecimentos/ocorrências/rastreamento por diário e checklist/respostas) fica para a 6B.
   - O front já envia Bearer. Hoje /auth/**, /usuarios/**, /turnos/**, /veiculos/**, /diarios/**, /rastreamento/**, /abastecimentos/**, /ocorrencias/**, /manutencoes/**, /logs/**, /secretarias/**, /mecanicos/**, /postos/**, /checklist/** estão permitAll.
   - Manter público só /auth/login (e /error). Definir quais rotas são só de gestor e quais de motorista (ROLE_GESTOR / ROLE_MOTORISTA).
   - Antes de fechar, conferir que o app Android também manda o token em todas as chamadas.

9. Bloqueios no servidor
   - FEITO (Parte 5, 19/10): abrir diário recusa sem PIN e com diário fechado pendente de assinatura. Interruptor APP_BLOQUEIOS_DIARIO (padrão ligado; false desliga este bloqueio e o do item 15). Corpo do erro com "codigo": PIN_AUSENTE ou DIARIO_PENDENTE_ASSINATURA. Rodar antes o diagnóstico do bloco "Parte 5" de docs/migracoes.sql (diários antigos pendentes podem travar motoristas).
   - Recusar abrir diário se o motorista tem diário "fechado" sem assinar, ou se não tem PIN cadastrado. Hoje só o app impede.

10. VeiculoResponse e edição
- FEITO (Parte 4, 19/10, falta deploy): tipoId, tipoNome e tipoMedidor reais nas listas (tipos numa consulta só); tipoId opcional no PUT (continua ignorado), obrigatório no POST (400 "Campo 'tipoId' é obrigatório."). Conferir no front quem dependia de tipoNome vazio.
- Incluir tipoId na resposta. Tornar tipoId opcional na edição (o UPDATE já o ignora).
- FEITO (19/10, aprovado): PUT /veiculos/{id}/ativo ({"ativo": true|false}); reativar volta como disponivel e é recusado (400) se outro veículo ativo usa a mesma placa.
- Status de veículo conhecidos: disponivel, em_uso, em_manutencao, inativo (DELETE só marca inativo).
- Achado 19/10: VeiculoService.toResponse devolve tipoNome = "" e tipoMedidor = "" fixos nas listas de veículos (só a listagem de veículos em uso traz o tipoNome). Devolver os valores reais (JOIN com tipo_veiculo) e conferir no front quem depende disso.

11. Swagger / OpenAPI (novo, pedido do Yago)
- Objetivo: pessoas de fora entenderem o funcionamento da API (orientador, banca, outros devs).
- Usar springdoc-openapi, linha 3.x (a 2.x é para Spring Boot 3). Na página oficial a versão atual é a 3.1.1: implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1"). Conferir a versão e a compatibilidade com o Spring Boot 4.1 na hora de adicionar.
- Esquema de segurança Bearer JWT (botão Authorize), tags por assunto (Autenticação, Diários, Assinatura, Veículos...), descrições e exemplos, e documentar o formato de erro {"erro": "..."}.
- Liberar /swagger-ui/** e /v3/api-docs/** na SecurityConfig. Fazer DEPOIS de fechar as rotas (item 8) e padronizar erros (item 2), senão a documentação pública expõe rotas abertas e um contrato que vai mudar.
- Considerar ligar/desligar por variável de ambiente (ex.: SWAGGER_ENABLED) para poder desativar em produção.
- Lembrar de gerar o jar de novo.

12. Operação
- Backup do Postgres do Render ANTES de ~16/11/2026 (o banco gratuito expira): pg_dump -h HOST -U USER -d DB --no-owner --no-privileges -f "C:\Users\yagom\backup-sgfe.sql"
- Trocar a senha do banco que foi exposta no chat.
- Futuro: Dockerfile multi-stage (compilar no Render) para não precisar versionar o jar.

13. GET /diarios com veiculoId e tipoMedidor (pedido do front, 18/10)
- FEITO (Parte 4, 19/10, falta deploy): veiculoId e tipoMedidor no DiarioResponse (lista, aberto, abrir, fechar); GET /diarios/{id} (reaproveita DiarioDetalheResponse da assinatura, + veiculoId, tipoMedidor, motivoUso); AbastecimentoResponse com comboio e veiculoAbastecidoId.
- O DiarioResponse hoje tem: id, veiculoDescricao, veiculoPlaca, motoristaNome, medidorInicial, medidorFinal, medidorPercorrido, destino, status, abertoEm, fechadoEm. Não tem veiculoId nem tipoMedidor.
- Sem eles, o front descobre o veículo pelo nome no Ranking e no Custo por Km (frágil se dois veículos tiverem descrição igual).
- Fazer junto com o item 10 (mudanças de resposta). É só acrescentar campos, não quebra o app.
- Pedido do modal de detalhes do diário (18/10): não existe GET /diarios/{id}. Criar uma rota de detalhe que devolva também motivoUso, observacaoFechamento, assinadoEm, veiculoId e tipoMedidor (a entidade DiarioBordo já tem motivoUsoId e observacaoFechamento; assinadoEm só aparece hoje no /verificar).
- AbastecimentoResponse (GET /abastecimentos/diario/{id}) não informa se foi abastecimento no comboio (veiculoAbastecidoId só existe no DetalhadoResponse).
- Os horários de abastecimento e ocorrência enviados pelo app com registradoEm ficam em horário de Brasília (item 1): no modal aparecem 3 h atrasados até corrigirmos a API.

14. Fotos de comprovação (pedido do app, 18/10)
- Foto do hodômetro ao fechar o diário (FecharDiarioRequest hoje: medidorFinal e observacaoFechamento).
- Foto do cupom fiscal no abastecimento em posto; foto da bomba quando o abastecimento é no comboio (AbastecimentoRequest hoje não tem foto).
- Padrão já existente: Manutencao.fotoNotaFiscal (TEXT base64). Mesmo padrão: colunas novas (ex.: diarios_bordo.foto_hodometro, abastecimentos.foto_comprovante) + campo opcional nos DTOs.
- NÃO devolver as fotos nas listas (lição do peso da foto de perfil): rotas separadas, ex.: GET /diarios/{id}/foto-hodometro e GET /abastecimentos/{id}/foto, com cache.
- Tamanho: o app comprime (lado maior ~1600 px, JPEG 85). Atenção ao espaço do banco gratuito (1 GB): estimar volume/mês e pensar em armazenamento externo.
- Decidir se o hash da assinatura passa a cobrir as fotos (hash de cada foto dentro do conteúdo assinado).
- O Jackson ignora campos desconhecidos: o app novo mandando foto para a API antiga NÃO dá erro e a foto se perde em silêncio. Por isso o app só liga as fotos depois da API pronta.

15. Turnos
- FEITO (Parte 5, 19/10): POST /turnos/fechar recusa só com diário aberto (400, mensagem combinada). Desliga com APP_BLOQUEIOS_DIARIO=false. Corpo do erro com "codigo": TURNO_COM_DIARIO_ABERTO.
- POST /turnos/fechar só valida "existe turno aberto". Decidir se o servidor deve recusar sair de serviço com diário aberto (hoje só o app bloquearia).
- DECIDIDO 19/10 (Parte 5): recusar SOMENTE se existir diário com status aberto, com {"erro": "Você tem um diário aberto. Feche o diário antes de sair de serviço."} e o mesmo status de erro que a rota usa hoje (400). Diário fechado pendente de assinatura NÃO impede sair de serviço (o bloqueio dele é ao abrir o próximo).

16. Manutenção responde 400 DEPOIS de gravar (achado 18/10) -- CAUSA CONFIRMADA E CORRIGIDA NO BANCO
- FEITO (Parte 3, 19/10, falta deploy): @Transactional em manutenção (abrir/status), ocorrência (registrar/status), abrir/fechar diário e abastecimento; log em transação própria (REQUIRES_NEW) e falha no log só vai para o log do servidor; log com o login do token quando houver, "sistema" quando não; controllers já não engolem exceção (Parte 1).
- Causa: o enum status_veiculo não tinha o valor em_manutencao. Corrigido em 18/10 com ALTER TYPE status_veiculo ADD VALUE IF NOT EXISTS 'em_manutencao'. O aviso sumiu.
- Efeito colateral a conferir: manutenções abertas antes da correção podem estar com o veículo em outro status e sem log MANUTENCAO_ABERTA.
- Falta ainda (melhorias): @Transactional, logar a exceção no controller, log com login real. Guardar as migrações num arquivo do repositório (docs/migracoes.sql).
- Plano 19/10: Parte 3 etapa A (manutenção + ocorrência) e etapa B (@Transactional em abrir/fechar diário, abastecimento e ocorrência, sem mudar a lógica). logService.registrar em transação própria (REQUIRES_NEW); falha no log não pode desfazer a operação principal. Nenhum service tem @Transactional hoje.
- Texto original do diagnóstico abaixo:
- Sintoma: POST /manutencoes e PUT /manutencoes/{id}/status criam/alteram o registro mas respondem 400 (o front mostra o aviso). Aparece desde a Parte 5A porque antes o front ignorava erros em silêncio.
- Causa: ManutencaoService não é @Transactional; cada save grava na hora. Depois do save vêm 2 passos que podem estourar: veiculoRepository.atualizarStatus(..., "em_manutencao") (CAST para o enum status_veiculo) e logService.registrar(usuarioLogin = "sistema", ...). O controller engole a exceção (catch RuntimeException -> 400 vazio) e nada aparece.
- Hipóteses: (1) o enum status_veiculo do banco não tem o valor em_manutencao (o código grava esse, o front antigo usava "manutencao"); (2) a tabela logs recusa o login "sistema" (chave estrangeira); (3) falha só ao montar a resposta.
- Como confirmar: logs do Render na hora da criação; ver se o veículo ficou em manutenção; ver se existe log MANUTENCAO_ABERTA; consultar o enum e as constraints da tabela logs.
- Correção possível: ALTER TYPE status_veiculo ADD VALUE IF NOT EXISTS 'em_manutencao' (só banco) ou ajustar o código; log com o login real do gestor (agora que há JWT) ou usuário "sistema" válido; @Transactional nos fluxos de escrita com vários passos (manutenção, abastecimento, fechar diário); logar a exceção no controller em vez de engolir.

17. Ruído e desempenho vistos nos logs do Render (18/10)
- FEITO (Parte 4, 19/10, falta deploy): logs 1+N -> 2 consultas (fotos em lote); lista de manutenções 1+2N -> 3 consultas (peças e veículos em lote). Ainda com consulta por linha: ocorrências detalhadas (4-5 por linha) e abastecimentos detalhados (foto por linha).
- O SQL do Hibernate está sendo impresso (show-sql ligado): enche o log e esconde os erros. Desligar quando terminar o diagnóstico (spring.jpa.show-sql=false).
- FEITO (Parte 1, 19/10, falta deploy): show-sql e format_sql desligados por padrão; ligar com SPRING_JPA_SHOW_SQL=true. ddl-auto passou a ter padrão none.
- Consultas repetidas: a tela de Logs consulta a foto do usuário uma vez por linha (findFotoByLogin dentro de toResponse); a lista de manutenções consulta peças e veículo uma vez por manutenção (toResponseSimples). Resolver com JOIN ou consulta em lote. Pesa no Render gratuito.

18. Status nas ocorrências + conclusão automática pela manutenção (pedido do Yago, 19/10)
- FEITO (Parte 3, 19/10, falta SQL + deploy): colunas status/resolvida_em (SQL antes do deploy em docs/migracoes.sql; backfill comentado, aguardando autorização); regras a-d e g; PUT /ocorrencias/{id}/status (aceita também "descartada"); status, resolvidaEm e manutencoes (id/status) nas respostas; filtro GET /ocorrencias?status=. Front e app: mostrar o status (depois do deploy).
- Situação hoje (lendo o código): a tabela `ocorrencias` NÃO tem status (colunas: id, diario_id, tipo, descricao, latitude, longitude, registrado_em). O vínculo com manutenção JÁ existe: `manutencoes.ocorrencia_id` (Manutencao.ocorrenciaId, ManutencaoRequest.ocorrenciaId opcional, ManutencaoResponse.ocorrenciaId, e ManutencaoRepository.findByOcorrenciaId já existe). Só falta o lado da ocorrência.
- Banco (migração idempotente, guardar em docs/migracoes.sql): `ALTER TABLE ocorrencias ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'aberta';` e, se quiser mostrar quando foi resolvida, `ALTER TABLE ocorrencias ADD COLUMN IF NOT EXISTS resolvida_em timestamp;`. Usar varchar e NÃO enum do Postgres (a lição do status_veiculo: enum sem o valor faz o UPDATE falhar).
- Valores sugeridos: `aberta` (registrada pelo motorista, ainda sem providência), `em_andamento` (tem manutenção vinculada em andamento), `concluida` (resolvida). Opcional: `descartada` (gestor avalia que não precisa de ação).
- Regras:
  a) Ocorrência nasce `aberta`.
  b) Ao criar manutenção com ocorrenciaId (ManutencaoService.abrir) -> ocorrência vira `em_andamento`.
  c) Ao concluir a manutenção (ManutencaoService.atualizarStatus com status "concluida") -> se a manutenção tem ocorrenciaId, consultar findByOcorrenciaId: SÓ marca a ocorrência `concluida` (e grava resolvida_em) se TODAS as manutenções vinculadas estiverem concluídas; senão continua `em_andamento`.
  d) Se a manutenção for reaberta (status volta para aberta/em_andamento) -> ocorrência volta para `em_andamento` e resolvida_em = null.
  e) Gestor também pode mudar o status à mão, para ocorrência que não gera manutenção (ex.: "pneu calibrado na hora"): PUT /ocorrencias/{id}/status com {status}. Validar o valor; recusar valor desconhecido com {"erro": ...}. (Se for só gestor, depende do item 8.)
  f) Tudo na MESMA transação (@Transactional): manutenção + ocorrência + log. Sem isso, repete o problema do item 16 (grava uma parte e responde erro).
  g) Registrar no log: OCORRENCIA_STATUS ("Ocorrência X passou para 'concluida' pela manutenção Y").
- Backfill das ocorrências que já existem (rodar uma vez depois do ALTER):
  `UPDATE ocorrencias o SET status='em_andamento' WHERE EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id=o.id AND m.status <> 'concluida');`
  `UPDATE ocorrencias o SET status='concluida' WHERE EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id=o.id) AND NOT EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id=o.id AND m.status <> 'concluida');`
  (as sem manutenção ficam `aberta`).
- Respostas: acrescentar `status` (e `resolvidaEm`) em OcorrenciaResponse e OcorrenciaDetalhadaResponse; em OcorrenciaDetalhadaResponse, se possível, também o id/status da manutenção vinculada. Só acrescenta campos, não quebra front nem app (Jackson ignora o que não conhece). O horário do evento (item 1) segue separado.
- Filtro: GET /ocorrencias com ?status= (ex.: listar só as abertas) ajuda a tela de gestão.
- Front (depois da API): badge de status da ocorrência (reusar o StatusBadge compartilhado), filtro por status, status no modal de detalhes do diário, e no modal "Nova manutenção" um campo opcional "Vincular a uma ocorrência" (conferir se já existe; a API já aceita ocorrenciaId).
- App (depois): mostrar o status da ocorrência nos detalhes do diário (item 1 da lista do app).
- TCC: descrever o ciclo da ocorrência (aberta -> em andamento -> concluída) no capítulo de funcionalidades.

19. Veículo volta a "disponivel" mesmo com outra manutenção aberta (achado lendo ManutencaoService, 19/10)
- FEITO (Parte 3, 19/10, falta deploy): ao concluir, outra manutenção aberta -> em_manutencao; diário aberto -> em_uso; senão disponivel.
- atualizarStatus, ao concluir uma manutenção, faz atualizarStatus(veiculo, "disponivel") sempre. Se o mesmo veículo tiver outra manutenção aberta ou em andamento, ele aparece como disponível e o motorista consegue abrir diário com ele.
- Correção: ao concluir, só voltar para `disponivel` se NÃO existir outra manutenção do veículo com status diferente de concluida (e tratar o caso do veículo estar com diário aberto -> em_uso). Fazer junto com o item 18 (mesma função, mesma transação).

20. Peça de manutenção sem edição (achado 19/10)
- FEITO (19/10, aprovado): PUT /manutencoes/pecas/{pecaId}; 400 dado/id inválido, 404 peça não existe, 409 manutenção concluída.
- PROPOSTA (Parte 4, aguardando OK): PUT /manutencoes/pecas/{pecaId}, ver relatório da Parte 4.
- Hoje só existem POST /manutencoes/{id}/pecas e DELETE /manutencoes/pecas/{pecaId}; não há como corrigir uma peça sem apagar e lançar de novo.
- Avaliar na Parte 4, como item opcional: PUT /manutencoes/pecas/{pecaId}.

21. Achados das consultas C1-C6 (19/10, Parte 6B)
- Ocorrências: a coluna ocorrencias.status JÁ EXISTIA (varchar com default 'pendente'::status_ocorrencia; enum status_ocorrencia = pendente, em_andamento, resolvida). O ALTER da Parte 3 (ADD COLUMN IF NOT EXISTS) não fez nada nessa coluna. Ocorrências antigas devem estar como 'pendente' (e talvez 'resolvida'), valores que a API não usa. Precisa de SQL: default 'aberta' e conversão pendente->aberta, resolvida->concluida (texto exato a aprovar).
- Placa: já existe o índice único veiculos_placa_key (placa exata, inclui veículos inativos). A decisão da Parte 2 de liberar a placa de veículo inativo não vale no banco: o cadastro com a mesma placa exata cai no índice (400 "Já existe um veículo com esta placa."). E "ABC-1D23" x "ABC1D23" passam no banco (a API barra).
- Perfil: o enum perfil_usuario tem 'administrador', mas o código só conhece gestor e motorista. Um usuário administrador quebra o carregamento (ex.: GET /usuarios) e não tem acesso nenhum com a matriz ligada.
- Triggers: fn_abre_diario (veículo em_uso + histórico) e fn_fecha_diario (calcula medidor_percorrido, grava veiculos.medidor_atual = medidor_final, veículo disponivel + histórico). O fechamento devolve o veículo para disponivel mesmo com manutenção aberta (mesmo problema do item 19). Com fila offline, um fechamento antigo chegando depois pode fazer o medidor_atual voltar para trás.
- Enum status_veiculo tem 'manutencao' e 'em_manutencao' (o código usa em_manutencao).
- checklist_respostas tem índice único (diario_id, item_id): reenviar o checklist hoje dá erro (500).
- Colunas de horário são todas "timestamp without time zone" (PASSO 0 da 6A ok para abastecimentos e ocorrências).
- Existem tabelas fotos (tipo_foto: medidor_abertura, medidor_fechamento, ocorrencia, livre) e cnh, sem uso pela API (item 14).

## Outras pendências do projeto (fora da API)
- Front: Partes 6, 7 e 8 do plano; escolher a paleta (A Petróleo, B Musgo ou C Grafite); ligar validarDigitosCpf antes de usar de verdade.
- Front (pedido 18/10): consertar badges com texto vazando (StatusBadge compartilhado) e modal de detalhes do diário ao clicar na linha (prompt entregue). Depois da API do item 18: status de ocorrência na interface.
- App Android: executar o prompt de correções (PIN obrigatório, bloquear diário pendente, botão voltar) e o redesenho por fases; depois fila offline com horário do evento. Status da ocorrência nos detalhes do diário depois do item 18.
- TCC: parágrafo 2.3.1 (assinatura eletrônica, conferir Lei 14.063/2020), linha em 1.3.2, trabalhos futuros 6.3 (prazo+bloqueio, PIN por abastecimento, conferência do gestor), capítulos 5 e 6; limitação do horário dos registros antigos; decidir se relatórios contam só diários assinados (hoje: fechados + assinados, com opção "Somente assinados").