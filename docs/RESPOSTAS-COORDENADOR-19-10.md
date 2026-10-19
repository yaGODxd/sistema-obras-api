# Respostas do coordenador — achados das consultas C1 a C6 e Parte 6B (19/10/2026)

Isto responde ao seu relatório dos achados a), b), c), d) e às perguntas 1 a 9. Siga a ordem abaixo.

## ORDEM DE TRABALHO
- PASSO 1 (agora): ajuste do vocabulário de status das ocorrências (achado a). Pequeno. Entregue um relatório curto no formato de sempre e PARE. O Yago vai subir o lote Parte 3 + Parte 5 + `codigo` junto com esse ajuste.
- PASSO 2: Parte 6B. Só comece quando o Yago mandar "pode começar a 6B".
- Registre tudo isto em docs/PENDENCIAS-API.md e docs/PLANO-API.md.

## PASSO 1 — Ocorrências (achado a)
RECUSO a correção proposta (SET DEFAULT 'aberta' e UPDATE pendente -> aberta / resolvida -> concluida). O vocabulário definitivo é o que o banco já tem:
- `pendente` (nasce assim), `em_andamento`, `resolvida`.
- Motivos: o enum status_ocorrencia faz parte do modelo de dados do projeto (vai para o TCC); assim nenhuma ocorrência existente é alterada; e front e app ainda não usam o status, então trocar o código agora é barato.
- Troque no código, nos testes, nas mensagens e nos docs: `aberta` -> `pendente`, `concluida` -> `resolvida`, SÓ para a OCORRÊNCIA. O status da MANUTENÇÃO continua aberta / em_andamento / concluida (não mexa).
- O INSERT da ocorrência pode omitir o status (o default do banco é 'pendente'). Confirme que o UPDATE/INSERT nativo com String funciona nessa coluna varchar, sem CAST.
- `?status=` em GET /ocorrencias: valide contra os três valores; valor desconhecido -> `{"erro": ..., "codigo": "STATUS_INVALIDO"}`.
- SQL para docs/migracoes.sql (idempotente, nesta ordem):
    1. normalização, só para o caso de o código antigo ter gravado valores velhos: `UPDATE ocorrencias SET status='pendente' WHERE status='aberta'; UPDATE ocorrencias SET status='resolvida' WHERE status='concluida';`
    2. o backfill da Parte 3 ajustado (resolvida em vez de concluida; grava resolvida_em com COALESCE);
    3. por último, `CHECK (status IN ('pendente','em_andamento','resolvida'))` (use DROP CONSTRAINT IF EXISTS antes do ADD, para poder rodar de novo). A coluna é varchar e o banco não barra valor errado.
- NÃO rode nem proponha `ALTER ... SET DEFAULT 'aberta'`.

## PASSO 2 — Parte 6B

### Respostas às perguntas 1 a 5 (reconfirmando o que já tinha sido enviado)
1. Horário no futuro: NÃO recusa até 24 h à frente; só marca suspeito (motivo HORARIO_FUTURO). Acima de 24 h recusa com 422 HORARIO_FUTURO (é dado errado, não relógio levemente adiantado). Horário com mais de 7 dias: só marca. Motivo: recusar prende o registro na fila do app e o motorista perde o abastecimento por causa de um relógio adiantado.
2. Desvio do relógio: só marcar como suspeito (limite sugerido: 5 min). Não corrigir o horário.
3. Fora do período do diário: só MARCAR (FORA_DA_JANELA_DIARIO). Recusas (409): evento para diário já ASSINADO (DIARIO_ASSINADO) e fechar um diário já fechado com outro idLocal (DIARIO_JA_FECHADO). Diário fechado e ainda não assinado ACEITA evento.
4. idLocal: sim em turno (abrir e fechar), diário (abrir e fechar), abastecimento, ocorrência e rastreamento. No checklist NÃO (ver abaixo).
5. Corte de dia e de mês em Brasília e datas com fuso nas respostas: parte separada, a 6C, depois da 6B.
   Lote de rastreamento: máximo de 200 pontos por chamada, documentado.

### Perguntas 6 a 9
6. Ocorrências: ver Passo 1 (recusado).
7. Placa: MANTER o índice atual. Inativo continua segurando a placa. Mensagem: "Já existe um veículo inativo com esta placa. Reative-o em vez de cadastrar novo." (a rota de reativar já existe). Isto substitui a decisão da Parte 2 de liberar a placa de inativo; corrija PLANO e PENDENCIAS. Normalização na API (cadastro e edição): maiúscula, sem hífen e sem espaço, e vazio vira NULL (o '' conta como valor no índice e dois '' colidem; vários NULL não).
8. Administrador: o Yago vai rodar uma contagem de perfis. Enquanto isso, torne a leitura do perfil tolerante (valor desconhecido não derruba GET /usuarios e não dá 500). Se existir administrador, eu volto com a regra de permissão.
9. Trigger do medidor: NÃO use GREATEST (um medidor digitado errado, alto demais, travaria o medidor_atual do veículo). Aprovo corrigir DENTRO do trigger (atômico), não depois no código:
    - a) `medidor_atual` só é gravado se este fechamento for o MAIS RECENTE por `fechado_em` entre os diários fechados ou assinados do mesmo veículo (usar COALESCE(NEW.fechado_em, NOW())). Assim um fechamento antigo que chega atrasado não faz o medidor andar para trás.
    - b) o status do veículo ao fechar: `em_manutencao` se existir manutenção do veículo com status diferente de 'concluida'; `inativo` continua `inativo`; senão `disponivel`.
    - c) o histórico grava o status real (antes e depois) e `alterado_em` = a hora do evento (fechado_em).
    - Faça o mesmo cuidado em `fn_abre_diario` (alterado_em = aberto_em, e o status real de antes).
    - Mostre o TEXTO EXATO das duas funções novas no relatório da 6B, e guarde o texto ORIGINAL (da consulta C4) em docs/migracoes.sql como bloco de ROLLBACK. O Yago só roda depois que eu revisar o texto.
    - Confirme que o código recusa medidor_final < medidor_inicial, que POST /diarios/{id}/fechar sempre manda medidor_final (o trigger só dispara com medidor_final não nulo) e que a API grava `fechado_em` = ocorridoEm (o trigger não preenche fechado_em).

### SQL proposto: aprovado, com estes ajustes
- turnos: acrescente também `horario_suspeito` e `motivo_suspeita`.
- diarios_bordo: acrescente `desvio_abertura_seg` e `desvio_fechamento_seg` (integer), pois é o documento assinado.
- rastreamento_gps: mantenha `id_local` (ele também serve para o lote devolver o resultado de cada ponto).
- checklist_respostas: sem id_local, só `recebido_em`. Reenvio: `INSERT ... ON CONFLICT (diario_id, item_id) DO UPDATE ... WHERE EXCLUDED.registrado_em >= checklist_respostas.registrado_em`, respondendo 200. Assim um reenvio antigo não desfaz uma resposta mais nova. Hoje o reenvio dá 500: registre como correção.
- id_local duplicado: só devolva o registro já gravado se ele pertencer ao motorista do token; caso contrário 409 `ID_LOCAL_EM_USO` (não vazar dado de outro motorista).
- Índices únicos parciais e `COALESCE(recebido_em, registrado_em)` no /rastreamento/posicoes: ok.
- Todas as colunas de data são `timestamp without time zone`: documente no topo de docs/migracoes.sql que elas guardam UTC por convenção. Não mude os tipos na 6B (a 6C cuida da serialização com fuso).

### Ponto novo: abastecimento de comboio offline
- Com veiculoAbastecidoId, o INSERT liga o abastecimento ao diário ABERTO do veículo abastecido e a regra exige que ele esteja aberto. Offline, quando sincronizar, esse diário pode já estar fechado: erro e o registro fica preso na fila.
- Na 6B, resolva o diário pelo veículo + hora do evento (aberto_em <= ocorridoEm <= COALESCE(fechado_em, agora)), aceitando diário fechado e ainda não assinado.
- O dono do token (motorista do comboio) não é o dono desse diário: proponha a regra de ControleAcesso para esse caso.
- abastecimentos não guarda QUEM registrou (só o log). Avalie uma coluna `registrado_por` (uuid, nula) e me diga sua opinião.

## Observações
- O volume é pequeno, então ALTER TABLE e índices novos não têm risco de tempo.
- Colunas `sincronizado` (abastecimentos e ocorrencias): confira se o código usa; não use para idempotência.
- Enum `resposta_checklist` e valor `manutencao` do status_veiculo são legado: não mexa agora.