Você vai trabalhar na API do SGFE (Kotlin + Spring Boot 4.1, PostgreSQL no Render). Esta mensagem é o plano de trabalho. Faça SOMENTE isto agora:
1. Crie a pasta docs/ se não existir e salve esta mensagem inteira em docs/PLANO-API.md. Esse arquivo governa o trabalho: releia-o sempre que o contexto for compactado.
2. Leia docs/PENDENCIAS-API.md, que tem o detalhe de cada item citado abaixo.
3. Responda ao PASSO 1. Não edite nenhum outro arquivo e não rode nada que suba a aplicação.

# Plano de melhorias da API (SGFE)

## Contexto
O sistema tem 3 peças: o front Angular (gestor), o app Android (motorista) e esta API. Front e app já estão em uso de teste e dependem do contrato atual da API. Eu (Yago) sou iniciante em git e em Render: explique em português simples e sem jargão solto.

## Fatos da API (confirmados antes; confira no código e avise se algo divergir)
- Banco: PostgreSQL no Render. O esquema é manual: toda mudança de banco é um SQL que EU rodo. CORRIGIDO 19/10: no código o padrão é ddl-auto=update (${SPRING_JPA_HIBERNATE_DDL_AUTO:update}), não none. Aprovado mudar o padrão para none na Parte 1. Valor da variável no Render: Yago vai conferir (pendente).
- JWT HS256 com {sub, perfil, iat, exp}. O exp está em MILISSEGUNDOS (24 h por padrão); front e app podem depender disso, então não mude sem plano. Login devolve {token, nome, perfil}. Existe GET /auth/me.
- Exigem token hoje: /auth/me, /usuarios/me/**, GET /diarios/meus, PUT /diarios/{id}, POST /diarios/{id}/assinar, GET /diarios/{id}/verificar. As outras rotas legadas são permitAll (serão fechadas na Parte 5). /error é permitAll.
- Erros hoje: os controllers antigos fazem catch (RuntimeException) e devolvem status 400 com corpo VAZIO; o login devolve 401 vazio; o AssinaturaController devolve {"erro": "..."}.
- Diário: aberto, fechado (pendente de assinatura), assinado. GET /diarios/{id}/verificar devolve {assinado, integro, assinadoEm, mensagem}. Fechar diário (POST /diarios/fechar/{login}) é um UPDATE direto em diarios_bordo (que calcula medidor_percorrido) mais a troca de status do veículo para disponivel; o código NÃO chama nenhuma função do banco. Quem atualiza veiculos.medidor_atual não aparece no código (talvez trigger: investigar na Parte 6).
- Manutenção: a única rota de alteração é PUT /manutencoes/{id}/status. Peça não tem edição: só POST /manutencoes/{id}/pecas e DELETE /manutencoes/pecas/{pecaId}.
- Abastecimento (achados 19/10): (a) o AbastecimentoService sempre calcula registradoEm em Kotlin, então o COALESCE(..., NOW()) do INSERT nunca é usado; a coluna sincronizado não é preenchida pelo INSERT (fica no default do banco). (b) Abastecimento e ocorrência se ligam ao diário aberto AGORA (status='aberto'); offline, o evento pode chegar depois do fechamento e falhar com "Motorista não tem diário aberto". (c) Abastecimento e ocorrência usam loginMotorista do CORPO, não do token. (d) O texto do log ABASTECIMENTO tem erro de precedência: o sufixo " (Comboio)" some quando há posto.
- Enums: PerfilUsuario em minúsculas (gestor, motorista). status_veiculo é enum do Postgres: disponivel, em_uso, em_manutencao, inativo. Status de manutenção é texto: aberta, em_andamento, concluida. Lição aprendida: enum do Postgres sem o valor faz o UPDATE falhar DEPOIS de outras gravações já feitas.
- Fuso: o Render roda em UTC e LocalDateTime sai sem fuso. EXCEÇÃO: AbastecimentoService e OcorrenciaService convertem o registradoEm opcional (epoch em ms) com America/Sao_Paulo e gravam hora de Brasília. O banco mistura os dois fusos (Parte 6).
- ManutencaoService não é @Transactional e o controller engole a exceção. Foi isso que causou "400 depois de gravar" (a causa no banco já foi corrigida).
- Ocorrência: a tabela ocorrencias NÃO tem status. O vínculo manutencoes.ocorrencia_id já existe (ManutencaoRepository.findByOcorrenciaId também).
- VeiculoService.toResponse devolve tipoNome = "" e tipoMedidor = "" fixos nas listas.
- LogService.registrar(usuarioLogin, usuarioNome, acao, descricao); a manutenção grava o login fixo "sistema".
- Consultas repetidas (N+1): a tela de logs busca a foto por linha; a lista de manutenções busca peças e veículo por manutenção. O show-sql está ligado e polui o log.
- Deploy: o Dockerfile faz COPY build/libs/*.jar. O jar é versionado DE PROPÓSITO. Depois de mudar o código: .\gradlew.bat bootJar e eu commito o jar. NUNCA ponha o jar no .gitignore e não altere o Dockerfile sem plano aprovado.
- O Jackson ignora campos desconhecidos: campo novo opcional não quebra cliente antigo, e cliente novo que manda campo para API antiga perde o dado em silêncio.
- Decisão do projeto: NÃO reduzir tamanho de foto (foto de perfil é base64 no banco).

## Regras de trabalho
1. Plano primeiro. Uma parte por vez. Você só começa a Parte N quando eu escrever "pode começar a parte N". Ao terminar, entregue o relatório e PARE.
2. Compatibilidade: só ACRESCENTAR campos; não renomear nem remover campos ou rotas; não mudar o status HTTP de caminhos de sucesso. Em respostas de erro, mudar o CORPO é permitido. Mudar o STATUS de um erro (ex.: 400 para 404 ou 409) é mudança de contrato: liste como "muda status" no plano e espere meu OK item a item, porque o app Android pode depender do status atual.
3. Banco: NUNCA conecte ao banco de produção e NÃO rode bootRun nem teste que suba o contexto Spring completo apontando para ele. Toda mudança de banco vira SQL em docs/migracoes.sql: idempotente (IF NOT EXISTS), aditiva e compatível com o código ANTERIOR. A ordem em produção é: eu rodo o SQL, depois faço o deploy do código. Cada bloco leva data e número da parte. Nada de DROP, DELETE ou UPDATE em massa sem eu aprovar o texto exato. Correção de dados existentes (backfill) fica em bloco separado e comentado.
4. Segredos: não escreva nem repita senha, URL de banco ou chave em arquivo ou resposta. Se achar credencial fixa no código ou no application.properties, avise (sem reproduzir) e proponha variável de ambiente.
5. Dependência nova (starter de validação, springdoc, bibliotecas de teste): só com meu OK no plano, informando nome, versão e motivo.
6. Testes sem banco real: controllers com MockMvc (@WebMvcTest), services com mocks. Reaproveite o que já existir.
7. Não faça commit nem push. Pode rodar .\gradlew.bat test e, no fim de cada parte, .\gradlew.bat bootJar. Diga se o jar mudou.
8. Mensagens de erro em português claro, sem detalhe técnico e sem stack.
9. Mantenha o estilo atual (pacotes com.example.sistemaobras.*) e mudanças pequenas e localizadas. Nada de refatoração fora do escopo da parte: o que achar de extra vai para "Achados" no relatório, sem mexer.
10. Ao fim de cada parte, atualize o CLAUDE.md (crie um curto se não existir) e marque o que foi feito em docs/PENDENCIAS-API.md.

## PASSO 1 (agora, sem editar nada além de salvar este plano)
Responda em até ~60 linhas:
a) Comandos de build e teste que funcionam no Windows (gradle wrapper); se já há testes e quais bibliotecas de teste existem.
b) Mapa das rotas, resumido por controller: método e rota, se exige token hoje, e como trata erro (400 vazio, {"erro"} ou outro).
c) Onde estão os try/catch que engolem exceção.
d) Configuração: show-sql, CORS, Jackson e fuso, e como o datasource é configurado (SEM mostrar valores secretos; diga só se há credencial fixa no código).
e) Para cada Parte 1 a 8, em até 6 linhas: o que fará, arquivos que tocará, risco, se precisa de SQL e se depende de mudança no app ou no front.
f) Divergências em relação aos "Fatos" e dúvidas.
Depois PARE e espere "pode começar a parte 1".

## PARTE 1 - Erros padronizados e log mais limpo (itens 2 e 17)
- Crie exceções de negócio (ex.: regra violada, não encontrado, conflito) em pacote próprio e um @RestControllerAdvice que SEMPRE responde {"erro": "mensagem em português"} (mesmo formato do AssinaturaController).
- Remova os try/catch que devolvem 400 vazio. Os services passam a lançar a exceção certa em vez de RuntimeException genérica, mantendo o STATUS atual de cada rota (regra 2).
- Trate também: JSON inválido, campo obrigatório ausente, UUID inválido, tipo errado, violação de unicidade do banco (mensagem amigável; identifique login, placa ou CPF quando der) e erro inesperado (500 com {"erro": "Erro interno. Tente novamente."} e a exceção completa no log do servidor, nunca na resposta).
- Login: mesma mensagem para usuário inexistente e senha errada ("Login ou senha incorretos."), status 401 como hoje; confira a senha ANTES de revelar que o usuário está inativo. Cadastro com login repetido: {"erro": "Já existe um usuário com este login."}.
- Se for viável sem mudar o comportamento, os 401 e 403 do Spring Security também em {"erro"}.
- spring.jpa.show-sql=false, com possibilidade de ligar por variável de ambiente.
- ddl-auto: padrão passa a ser none (${SPRING_JPA_HIBERNATE_DDL_AUTO:none}). Aprovado em 19/10. Nenhum bootRun apontando para o banco.
- Rotas a verificar uma a uma (as que o front mapeou): POST /auth/cadastrar; PUT /usuarios/{login}, /usuarios/{login}/ativo, /usuarios/{login}/perfil; POST, PUT e DELETE /veiculos; POST e PUT /mecanicos e /mecanicos/{id}/ativo; POST e PUT /postos e /postos/{id}/ativo; POST /checklist/itens e PUT /checklist/itens/{id}/ativo; POST /manutencoes e PUT /manutencoes/{id}/status.
- Testes: o advice (cada tipo de erro) e pelo menos uma rota de cada controller alterado.

## PARTE 2 - Validações e unicidade (item 3)
- Bean Validation (provavelmente exige o starter de validação: peça OK): recusar valores negativos em medidor, custos, valor, litros e quantidade de peça; textos obrigatórios e tamanhos compatíveis com as colunas.
- Unicidade: antes de gravar, checar login, placa e CPF e responder com mensagem clara (status conforme regra 2). Placa é OPCIONAL (máquinas não têm). Em docs/migracoes.sql, índices únicos só como sugestão COMENTADA, junto com a consulta que mostra duplicados já existentes (eu decido se rodo).
- CPF: crie a validação de dígitos verificadores, mas DESLIGADA por padrão (propriedade app.validar-cpf-digitos=false, ligável por variável de ambiente). Estamos em fase de testes. Combina com o interruptor validarDigitosCpf do front.

## PARTE 3 - Ocorrência com status + manutenção (itens 18, 19 e 16) - FEITA em 19/10 (falta SQL e deploy)
### Etapa A - manutenção e ocorrência
- Siga o item 18 do docs/PENDENCIAS-API.md: coluna status (texto, NÃO enum) em ocorrencias, valores aberta, em_andamento e concluida (diga no plano se vale incluir "descartada"), mais resolvida_em; SQL do ALTER e do backfill em docs/migracoes.sql.
- Regras: manutenção criada com ocorrenciaId deixa a ocorrência em_andamento; ao concluir a manutenção, a ocorrência vira concluida SOMENTE se todas as manutenções vinculadas estiverem concluídas; se a manutenção for reaberta, a ocorrência volta para em_andamento. Novo PUT /ocorrencias/{id}/status para o gestor mudar à mão (valor desconhecido é recusado com {"erro"}). Acrescente status e resolvidaEm nas respostas de ocorrência e um filtro ?status= na listagem.
- Status de manutenção (decisão 19/10): PUT /manutencoes/{id}/status só aceita aberta, em_andamento e concluida; outro valor é recusado com {"erro"} (status da rota: 400, como hoje).
- Item 19: ao concluir uma manutenção, o veículo só volta para disponivel se NÃO houver outra manutenção do mesmo veículo não concluída (e se tiver diário aberto, fica em_uso).
- Item 16: @Transactional nos fluxos de escrita com vários passos (manutenção, ocorrência e log na MESMA transação); logar a exceção em vez de engolir; usar o login do usuário autenticado no log quando houver token válido, "sistema" quando não houver.
- Log novo OCORRENCIA_STATUS.
- Testes com mock: uma manutenção concluída; duas vinculadas (só conclui quando ambas concluírem); reabrir; veículo com outra manutenção aberta.
### Etapa B - transações nos outros fluxos (depois da Etapa A)
- @Transactional em abrir diário, fechar diário, registrar abastecimento e registrar ocorrência, SEM mudar a lógica.
- logService.registrar roda em transação própria (Propagation.REQUIRES_NEW) e uma falha no log NÃO pode desfazer a operação principal: capturar a exceção FORA da transação do log (no Spring, exceção dentro da mesma transação a marca como rollback mesmo se capturada). Vale também para a Etapa A.
- Conferir que os services são classes abertas (plugin kotlin-spring abre classes @Service; confirmar no build).
- Corrigir a precedência no texto do log ABASTECIMENTO (sufixo " (Comboio)" deve aparecer também quando há posto).
- Validações decididas em 19/10:
  - Recusar litros = 0 (litros > 0) e quantidade de peça menor que 1.
  - NÃO recusar valorTotal = 0 (comboio pode não ter valor), custo de mão de obra = 0, nem valor unitário de peça = 0 (garantia).
  - Fechar diário: recusar medidor final menor que o inicial com {"erro": "O medidor final não pode ser menor que o medidor inicial."} (vale para as duas rotas de fechar). Troca de hodômetro fica como limitação documentada; exceção só se a Secretaria pedir.

## PARTE 4 - Respostas mais completas e consultas mais leves (itens 10, 13 e resto do 17) - FEITA em 19/10 (falta deploy; opcionais aguardam OK)
- VeiculoResponse: tipoId, tipoNome e tipoMedidor REAIS nas listas (hoje vão vazios; confira no front quem depende disso); tipoId opcional na edição.
- DiarioResponse: acrescentar veiculoId e tipoMedidor. Nova rota GET /diarios/{id} (detalhe) com motivoUso, observacaoFechamento, assinadoEm, veiculoId e tipoMedidor.
- AbastecimentoResponse: informar se foi abastecimento no comboio.
- N+1: foto na tela de logs e peças/veículo na lista de manutenções. Resolver com JOIN ou busca em lote e mostrar no relatório a contagem de consultas antes e depois.
- OPCIONAL (avaliar e propor): PUT /manutencoes/pecas/{pecaId} para editar peça (hoje só existe adicionar e remover).
- OPCIONAL (decisão 19/10): rota para reativar veículo inativo (hoje DELETE só inativa e não há volta). A placa de veículo inativo continua liberada para novo cadastro.
- Só acrescentar campos.

## PARTE 5 - Segurança: rotas atrás do JWT, bloqueios no servidor, CORS (itens 8, 9, 15 e 4)
- No plano, traga a matriz rota x perfil (gestor, motorista, ambos) para eu aprovar. Público: só /auth/login e /error.
- Interruptor por variável de ambiente (ex.: app.seguranca.exigir-jwt, padrão false) para eu ligar no Render só depois de confirmar que o app Android manda o token em todas as chamadas, podendo desligar sem novo deploy.
- 401 (sem token) e 403 (sem permissão) em {"erro"}.
- CORS: Authorization em allowedHeaders; origens do front (localhost:4200 e o domínio publicado) por configuração.
- Bloqueios no servidor: recusar abrir diário se o motorista tem diário fechado sem assinar ou não tem PIN cadastrado. Onde fizer sentido, o motorista só mexe nos próprios diários.
- Item 15 (decidido 19/10): POST /turnos/fechar recusa SOMENTE se existir diário com status aberto, com {"erro": "Você tem um diário aberto. Feche o diário antes de sair de serviço."} e o mesmo status de erro que a rota usa hoje (400). Diário fechado pendente de assinatura NÃO impede sair de serviço (o bloqueio dele é ao abrir o próximo diário).
- Identidade: abastecimento e ocorrência (e demais rotas do motorista) passam a usar o login do TOKEN quando houver token válido, em vez de loginMotorista do corpo.
- Não altere o formato do exp do token sem me avisar.

## PARTE 6 - Horário real do evento (uso offline) e fuso (itens 1, 5 e 6)
- Publicar a API ANTES do app. Banco guarda UTC.
- Dividida em 6A e 6B. A 6A foi executada ANTES da Parte 3 (decisão 19/10). A "Parte 6 antiga" é a 6B.

### Parte 6A - fuso (FEITA em 19/10, falta deploy e a correção dos registros antigos)
- Não exige mudança no app (o epoch é um instante absoluto).
- (a) registradoEm convertido com ZoneOffset.UTC em AbastecimentoService e OcorrenciaService.
- (b) Hora do servidor sempre UTC explícito: LocalDateTime.now(ZoneOffset.UTC) no código; fuso padrão da aplicação fixado em UTC no main(); cada conexão do banco com SET TIME ZONE 'UTC' (o NOW() do SQL grava UTC).
- (c) Registros antigos: docs/migracoes.sql, bloco "Parte 6A". PASSO 1 = pré-visualização (contagem e amostra) casando cada registro com o log da mesma requisição; PASSO 2 = UPDATE +3 h só nos classificados "corrigir_mais_3h", em bloco separado e comentado, com tabela de controle para desfazer. Yago roda o PASSO 1, mostra o resultado e só então autoriza o PASSO 2. Abastecimento de diário já assinado NÃO é corrigido (entra no resumo da assinatura).
- Ficaram para depois (definir se entram na 6B ou numa 6C): corte de dia (/logs) e de mês (/diarios) em America/Sao_Paulo e serialização de datas com fuso.

### Itens comuns
- Corrija a conversão do registradoEm (abastecimento e ocorrência) de America/Sao_Paulo para UTC. (Feito na 6A.)
- Campos opcionais novos ocorridoEm (ISO-8601 UTC com Z) e idLocal (UUID) nos eventos listados na 6B. Reenvio com o mesmo idLocal devolve o registro existente, sem duplicar e sem erro. Sem o campo, usa now() como hoje.

### Parte 6B - horário do evento capturado no celular (decisão do Yago, 19/10)
- Regra: TODO horário de evento é o capturado pelo celular no momento da ação (instante absoluto em UTC), para o uso offline funcionar.
- Eventos cobertos: abrir e fechar TURNO, abrir e fechar DIÁRIO, ABASTECIMENTO, OCORRÊNCIA, RASTREAMENTO e respostas de CHECKLIST.
- Exceção: a hora da ASSINATURA do diário continua sendo a do servidor.
- Situação atual (conferida no código em 19/10): só AbastecimentoRequest e OcorrenciaRequest têm campo de horário (registradoEm, epoch em ms, convertido com America/Sao_Paulo). Os outros usam o relógio do servidor: turno abrir/fechar (NOW() no TurnoRepository), diário abrir/fechar (NOW() no DiarioBordoRepository), rastreamento (NOW() no RastreamentoRepository) e checklist (LocalDateTime.now() na entidade ChecklistResposta). TurnoRequest, FecharDiarioRequest, AbrirDiarioRequest, RastreamentoRequest e ChecklistRespostaRequest não têm campo de horário.
- Atenção: GET /rastreamento/posicoes filtra "registrado_em > NOW() - 2 minutos". Se registrado_em passar a ser a hora do evento, um ponto enviado atrasado (offline) não aparece como posição atual; avaliar usar a hora do recebimento nesse filtro.
- Avaliar no plano da 6B:
  a) Guardar a hora do recebimento (servidor) separada da hora do evento, em todas as tabelas de evento.
  b) O app mandar enviadoEm (hora do aparelho no momento do envio). A API calcula o desvio do relógio (recebidoEm - enviadoEm) e pode sinalizar ou corrigir ocorridoEm. Dizer se corrige automaticamente ou só sinaliza, e com que tolerância.
  c) Recusar ou sinalizar horário no futuro (com tolerância) e muito antigo.
  d) Recusar ou sinalizar evento fora da janela aberto_em..fechado_em do diário (abastecimento, ocorrência, rastreamento, checklist) e, para o diário, fora da janela do turno.
  e) Como sinalizar (ex.: coluna horario_suspeito / motivo) e onde o gestor vê isso.

### Parte 6B - acréscimo 2 (relatório do app, confirmado no código dele, 19/10)
Com a fila offline, o app envia os eventos de cada diário SEPARADOS do momento em que o diário está aberto. A API não pode depender de "diário aberto AGORA".
1. diarioId opcional (o id que a API devolveu ao abrir) em abastecimento, ocorrência, rastreamento e respostas de checklist. Validar: o diário é do motorista do token, e ocorridoEm cai na janela aberto_em..fechado_em (diário ainda aberto: entre aberto_em e agora). Sem diarioId, continua o comportamento atual. (Checklist já recebe diarioId hoje, mas sem validar dono nem janela.)
2. Nova rota POST /diarios/{id}/fechar (a POST /diarios/fechar/{login} continua existindo), com ocorridoEm e idLocal opcionais, idempotente: repetir o mesmo idLocal devolve o resultado já gravado.
3. POST /diarios/abrir também aceita ocorridoEm e idLocal (repetir o idLocal devolve o diário existente). Hoje o app escreve "Diário aberto em modo offline em dd/MM/yyyy HH:mm" na observação; isso sai do app depois.
4. Nova rota POST /rastreamento/lote: diarioId + lista de pontos (latitude, longitude, velocidade, ocorridoEm, idLocal). Aceita pontos de diário já fechado dentro da janela, mantém o descarte de pontos muito próximos e é idempotente por idLocal.
   - Hoje o descarte (RastreamentoRepository.contarPontosProximos) ignora ponto a menos de 50 m de outro do diário ABERTO registrado nos últimos 2 min pelo relógio do SERVIDOR (NOW()). No lote, o descarte precisa usar ocorridoEm (pontos próximos no espaço E no tempo do evento), inclusive entre pontos do mesmo lote, e funcionar para diário fechado.
   - Achado: a fórmula usa acos(...) sem limitar o argumento a [-1, 1]; para pontos idênticos o arredondamento pode passar de 1 e o Postgres dá erro "input is out of range" (hoje vira 400 em POST /rastreamento). Corrigir junto (LEAST/GREATEST).
5. Turno: POST /turnos/abrir e /turnos/fechar também com ocorridoEm.
6. O app precisa distinguir erro de rede/servidor (tentar de novo) de erro de regra (não tentar de novo). Nas rotas NOVAS usar status coerentes (ex.: 400 dado inválido, 403 diário de outro motorista, 404 diário não existe, 409 conflito de estado, 422 fora da janela, 5xx só para falha do servidor) e listar cada um no relatório. Rotas antigas mantêm o contrato (regra 2).
- Não se sabe ainda se o app envia registradoEm (Yago vai perguntar ao Claude Code do app). Planejar para os DOIS cenários (envia / não envia). Propor (sem rodar) a consulta de diagnóstico: comparar logs.criado_em (acao ABASTECIMENTO, mesmo login) com abastecimentos.registrado_em; diferença de ~3 h = o app enviou registradoEm; ~0 = não enviou.
- Aceitar diarioId opcional em abastecimento e ocorrência e validar que o horário do evento está na janela aberto_em..fechado_em daquele diário (hoje só liga ao diário aberto AGORA, o que falha no offline).
- Descobrir no código (e depois nos triggers) quem calcula medidor_percorrido e veiculos.medidor_atual.
- Antes de propor mudança nas tabelas, me peça os resultados das consultas (information_schema e lista de triggers) e me diga exatamente quais rodar. Yago passa a lista de triggers nessa hora.
- Corte de dia (/logs) e de mês (/diarios) em America/Sao_Paulo. Serialização de datas com fuso consistente: proponha uma forma compatível (o interceptor do front só normaliza texto SEM fuso) e liste o risco para o app.

## PARTE 7 - Swagger/OpenAPI (item 11) - só depois das Partes 1 e 5
- springdoc-openapi linha 3.x. Confirme a versão exata e a compatibilidade com o nosso Spring Boot ANTES de adicionar (peça OK).
- Esquema Bearer JWT (botão Authorize), tags por assunto em português, descrições e exemplos dos DTOs principais, documentar o erro {"erro"} e os códigos de status.
- Liberar /swagger-ui/** e /v3/api-docs/** na SecurityConfig; ligar e desligar por SWAGGER_ENABLED; gerar o jar de novo.
- Objetivo: orientador, banca e outros devs entenderem a API.

## PARTE 8 - Operação (item 12) - só plano e documentação
- Passo a passo em português simples (Windows/PowerShell) do pg_dump do banco do Render ANTES de ~16/11/2026, e de como trocar a senha do banco (a atual foi exposta) e atualizar a variável no Render.
- README curto da API: como rodar, variáveis de ambiente, ordem SQL -> deploy.
- PROPOSTA (sem executar) de Dockerfile multi-stage para o Render compilar sozinho e o jar parar de ser versionado, com riscos. Só executar se eu aprovar.

## Relatório ao fim de cada parte (depois PARE)
1. O que mudou (arquivos, 1 linha cada).
2. SQL para rodar ANTES do deploy (bloco pronto para colar) ou "nenhum".
3. Variáveis de ambiente novas no Render ou "nenhuma".
4. Resultado de .\gradlew.bat test e de .\gradlew.bat bootJar; o jar mudou? (sim/não).
5. O que eu devo testar (requisições concretas e resultado esperado, para Bruno/Swagger).
6. Impacto no front e no app (o que eles precisam mudar, se algo).
7. Como desfazer (rollback).
8. Achados fora do escopo.
   Ordem de deploy: rodar o SQL, commitar (com o jar), dar push, esperar o Render (o primeiro acesso demora cerca de 1 minuto) e testar.