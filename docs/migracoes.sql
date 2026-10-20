-- Migrações da API SGFE.
-- Regras: idempotente (IF NOT EXISTS), aditiva e compatível com o código anterior.
-- Ordem em produção: o Yago roda o SQL e DEPOIS faz o deploy do código.
-- Backfill (correção de dados existentes) fica em bloco separado e comentado.
--
-- CONVENÇÃO DE HORÁRIO: todas as colunas de data são "timestamp without time zone"
-- e guardam UTC. O fuso de Brasília é só para exibição e cortes de dia/mês (Parte 6C).
-- Não mudar os tipos das colunas.

-- =====================================================================
-- 19/10/2026 - Parte 2 (validações e unicidade)
-- Nada obrigatório: a API já confere login, CPF e placa antes de gravar.
-- Os blocos abaixo são SUGESTÕES COMENTADAS. Rode só o que decidir rodar.
-- =====================================================================

-- 1) Ver os índices e restrições únicas que já existem (só leitura):
-- SELECT tablename, indexname, indexdef
-- FROM pg_indexes
-- WHERE schemaname = 'public' AND tablename IN ('usuarios', 'veiculos')
-- ORDER BY tablename, indexname;

-- 2) Procurar duplicados que impediriam criar os índices (só leitura):
-- CPF (compara só os dígitos):
-- SELECT regexp_replace(cpf, '[^0-9]', '', 'g') AS cpf_digitos, COUNT(*) AS qtd,
--        string_agg(login, ', ') AS logins
-- FROM usuarios
-- GROUP BY 1 HAVING COUNT(*) > 1;
--
-- Placa (sem traço/espaço, maiúsculas, ignorando vazia e veículo inativo):
-- SELECT upper(regexp_replace(placa, '[^A-Za-z0-9]', '', 'g')) AS placa_normalizada, COUNT(*) AS qtd,
--        string_agg(descricao, ', ') AS veiculos
-- FROM veiculos
-- WHERE placa IS NOT NULL AND placa <> '' AND status != 'inativo'
-- GROUP BY 1 HAVING COUNT(*) > 1;
--
-- Login:
-- SELECT login, COUNT(*) FROM usuarios GROUP BY login HAVING COUNT(*) > 1;

-- 3) Índices únicos (só depois que as consultas do passo 2 voltarem vazias).
-- Se o passo 1 já mostrar um índice único equivalente, não crie outro.
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_login ON usuarios (login);
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_cpf ON usuarios ((regexp_replace(cpf, '[^0-9]', '', 'g')));
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_veiculos_placa
--     ON veiculos ((upper(regexp_replace(placa, '[^A-Za-z0-9]', '', 'g'))))
--     WHERE placa IS NOT NULL AND placa <> '' AND status != 'inativo';

-- 4) Tamanho das colunas de texto (só leitura). Mande o resultado para
--    a API ganhar limites de tamanho iguais aos do banco:
-- SELECT table_name, column_name, data_type, character_maximum_length, is_nullable
-- FROM information_schema.columns
-- WHERE table_schema = 'public'
--   AND table_name IN ('usuarios', 'veiculos', 'mecanicos', 'postos', 'manutencoes', 'manutencao_pecas',
--                      'abastecimentos', 'ocorrencias', 'diarios_bordo', 'checklist_itens')
--   AND data_type IN ('character varying', 'character')
-- ORDER BY table_name, ordinal_position;

-- 5) (19/10) usuarios.foto_perfil aparece como varchar(255), mas a foto de perfil
--    é base64 (bem maior que 255). Diagnóstico (só leitura):
-- SELECT column_name, data_type, character_maximum_length
-- FROM information_schema.columns
-- WHERE table_name = 'usuarios' AND column_name = 'foto_perfil';
-- SELECT COUNT(*) AS com_foto, MAX(length(foto_perfil)) AS maior_foto FROM usuarios WHERE foto_perfil IS NOT NULL;
--
-- Se o tipo for mesmo varchar(255), a correção (aditiva, não perde dados) seria:
-- ALTER TABLE usuarios ALTER COLUMN foto_perfil TYPE text;
-- (NÃO rode sem combinar: precisa do OK do Yago no texto exato.)
-- -> APROVADO em 20/10: ver o bloco "Foto de perfil" no fim do arquivo.

-- =====================================================================
-- 19/10/2026 - Parte 6A (fuso): registros antigos gravados em hora de Brasília
-- =====================================================================
-- Contexto: até a Parte 6A, quando o app mandava registradoEm (epoch em ms),
-- a API gravava abastecimentos.registrado_em e ocorrencias.registrado_em em hora
-- de BRASÍLIA (3 h atrás do UTC). Sem registradoEm, gravava a hora do servidor (UTC).
--
-- Como separar os dois casos com segurança: cada registro tem um log gravado NA MESMA
-- requisição (logs.criado_em = hora do recebimento, em UTC). O registro é casado com o
-- log pelo login + texto do log (tipo/valores), e a diferença decide:
--   - log até 2 min depois de registrado_em  -> hora do servidor, está certo (não mexe);
--   - log 2h55 ou mais depois                -> hora de Brasília (+3 h corrige);
--   - nenhum log casado, mais de um, ou diferença entre 2 min e 2h55 -> "sem_log_seguro" (não mexe).
-- Abastecimento de diário JÁ ASSINADO não é corrigido: o horário dele entra no resumo
-- da assinatura (AssinaturaService) e a verificação passaria a dizer "não íntegro".
--
-- ORDEM: 1) deploy da 6A; 2) PASSO 0 e PASSO 1 (só leitura); 3) mandar o resultado;
--        4) só com autorização: PASSO 2.

-- PASSO 0 (só leitura): conferir que as colunas são "timestamp without time zone".
-- Se aparecer "with time zone", PARE e avise.
-- SELECT table_name, column_name, data_type
-- FROM information_schema.columns
-- WHERE table_schema = 'public'
--   AND (table_name, column_name) IN (('abastecimentos','registrado_em'), ('ocorrencias','registrado_em'), ('logs','criado_em'));

-- PASSO 1 (só leitura). Rode o bloco inteiro na mesma janela/sessão.
-- As TEMP VIEWs somem sozinhas ao fechar a sessão (não alteram o banco).
-- TROQUE a data do corte pela data/hora (UTC) em que o deploy da 6A ficou no ar.
/*
CREATE OR REPLACE TEMP VIEW vw_6a_parametros AS
SELECT TIMESTAMP '2026-10-20 00:00:00' AS corte;   -- <<< deploy da 6A, em UTC

CREATE OR REPLACE TEMP VIEW vw_6a_abastecimentos AS
WITH abast AS (
    SELECT a.id, a.registrado_em, a.veiculo_abastecido_id, u.login,
           (d.assinatura_hash IS NOT NULL) AS diario_assinado,
           'Abastecimento registrado — ' || a.tipo_combustivel || ', ' AS prefixo,
           to_char(a.litros, 'FM999999990.0') || ' L, R$ ' || to_char(a.valor_total, 'FM999999990.00') AS val_ponto,
           replace(to_char(a.litros, 'FM999999990.0'), '.', ',') || ' L, R$ '
             || replace(to_char(a.valor_total, 'FM999999990.00'), '.', ',') AS val_virgula
    FROM abastecimentos a
    JOIN diarios_bordo d ON d.id = a.diario_id
    JOIN usuarios u ON u.id = d.usuario_id
),
casados AS (
    -- no comboio o log fica com o login de quem abasteceu (outro motorista), então não exige login
    SELECT x.id, l.id AS log_id, l.criado_em, l.criado_em - x.registrado_em AS diferenca
    FROM abast x
    JOIN logs l ON l.acao = 'ABASTECIMENTO'
     AND (x.veiculo_abastecido_id IS NOT NULL OR l.usuario_login = x.login)
     AND (l.descricao LIKE x.prefixo || x.val_ponto || '%' OR l.descricao LIKE x.prefixo || x.val_virgula || '%')
     AND l.criado_em BETWEEN x.registrado_em - INTERVAL '1 minute' AND x.registrado_em + INTERVAL '30 days'
),
tres_horas AS (
    SELECT c.*, COUNT(*) OVER (PARTITION BY c.log_id) AS registros_no_mesmo_log
    FROM casados c WHERE c.diferenca >= INTERVAL '2 hours 55 minutes'
),
resumo AS (
    SELECT x.id, x.registrado_em, x.login, x.diario_assinado,
           (SELECT COUNT(*) FROM casados c WHERE c.id = x.id AND c.diferenca <= INTERVAL '2 minutes') AS logs_na_hora,
           (SELECT COUNT(*) FROM tres_horas t WHERE t.id = x.id) AS logs_3h,
           (SELECT MAX(t.registros_no_mesmo_log) FROM tres_horas t WHERE t.id = x.id) AS registros_no_mesmo_log,
           (SELECT MIN(t.criado_em) FROM tres_horas t WHERE t.id = x.id) AS log_em
    FROM abast x
)
SELECT r.*, r.log_em - r.registrado_em AS diferenca,
       CASE
         WHEN r.logs_na_hora > 0 THEN 'ok_hora_do_servidor'
         WHEN r.logs_3h = 1 AND r.registros_no_mesmo_log = 1 AND r.log_em < p.corte AND r.diario_assinado THEN 'brasilia_diario_assinado_nao_corrigir'
         WHEN r.logs_3h = 1 AND r.registros_no_mesmo_log = 1 AND r.log_em < p.corte THEN 'corrigir_mais_3h'
         ELSE 'sem_log_seguro'
       END AS classe
FROM resumo r CROSS JOIN vw_6a_parametros p;

CREATE OR REPLACE TEMP VIEW vw_6a_ocorrencias AS
WITH ocorr AS (
    SELECT o.id, o.registrado_em, u.login,
           'Ocorrência registrada — Tipo: ' || o.tipo || ', Descrição: ' || o.descricao AS texto_log
    FROM ocorrencias o
    JOIN diarios_bordo d ON d.id = o.diario_id
    JOIN usuarios u ON u.id = d.usuario_id
),
casados AS (
    SELECT x.id, l.id AS log_id, l.criado_em, l.criado_em - x.registrado_em AS diferenca
    FROM ocorr x
    JOIN logs l ON l.acao = 'OCORRENCIA' AND l.usuario_login = x.login
     AND (l.descricao = x.texto_log OR (length(x.texto_log) > 255 AND l.descricao = left(x.texto_log, 254) || '…'))
     AND l.criado_em BETWEEN x.registrado_em - INTERVAL '1 minute' AND x.registrado_em + INTERVAL '30 days'
),
tres_horas AS (
    SELECT c.*, COUNT(*) OVER (PARTITION BY c.log_id) AS registros_no_mesmo_log
    FROM casados c WHERE c.diferenca >= INTERVAL '2 hours 55 minutes'
),
resumo AS (
    SELECT x.id, x.registrado_em, x.login,
           (SELECT COUNT(*) FROM casados c WHERE c.id = x.id AND c.diferenca <= INTERVAL '2 minutes') AS logs_na_hora,
           (SELECT COUNT(*) FROM tres_horas t WHERE t.id = x.id) AS logs_3h,
           (SELECT MAX(t.registros_no_mesmo_log) FROM tres_horas t WHERE t.id = x.id) AS registros_no_mesmo_log,
           (SELECT MIN(t.criado_em) FROM tres_horas t WHERE t.id = x.id) AS log_em
    FROM ocorr x
)
SELECT r.*, r.log_em - r.registrado_em AS diferenca,
       CASE
         WHEN r.logs_na_hora > 0 THEN 'ok_hora_do_servidor'
         WHEN r.logs_3h = 1 AND r.registros_no_mesmo_log = 1 AND r.log_em < p.corte THEN 'corrigir_mais_3h'
         ELSE 'sem_log_seguro'
       END AS classe
FROM resumo r CROSS JOIN vw_6a_parametros p;

-- Contagem por classe (mande este resultado):
SELECT 'abastecimentos' AS tabela, classe, COUNT(*) FROM vw_6a_abastecimentos GROUP BY classe
UNION ALL
SELECT 'ocorrencias', classe, COUNT(*) FROM vw_6a_ocorrencias GROUP BY classe
ORDER BY 1, 2;

-- Amostra (mande também):
(SELECT 'abastecimentos' AS tabela, id, login, registrado_em, log_em, diferenca, classe
 FROM vw_6a_abastecimentos ORDER BY classe, registrado_em DESC LIMIT 15)
UNION ALL
(SELECT 'ocorrencias', id, login, registrado_em, log_em, diferenca, classe
 FROM vw_6a_ocorrencias ORDER BY classe, registrado_em DESC LIMIT 15);
*/

-- PASSO 2 - CORREÇÃO (backfill). NÃO RODE sem autorização do texto exato.
-- Precisa das TEMP VIEWs do PASSO 1 criadas na MESMA sessão.
-- Guarda o valor antigo em correcao_fuso_6a (permite desfazer) e só soma 3 h em quem
-- ainda está com o valor antigo: rodar duas vezes não soma 6 h.
/*
BEGIN;

CREATE TABLE IF NOT EXISTS correcao_fuso_6a (
    tabela              text      NOT NULL,
    registro_id         uuid      NOT NULL,
    registrado_em_antes timestamp NOT NULL,
    corrigido_em        timestamp NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    PRIMARY KEY (tabela, registro_id)
);

INSERT INTO correcao_fuso_6a (tabela, registro_id, registrado_em_antes)
SELECT 'abastecimentos', id, registrado_em FROM vw_6a_abastecimentos WHERE classe = 'corrigir_mais_3h'
ON CONFLICT DO NOTHING;

INSERT INTO correcao_fuso_6a (tabela, registro_id, registrado_em_antes)
SELECT 'ocorrencias', id, registrado_em FROM vw_6a_ocorrencias WHERE classe = 'corrigir_mais_3h'
ON CONFLICT DO NOTHING;

UPDATE abastecimentos a SET registrado_em = c.registrado_em_antes + INTERVAL '3 hours'
FROM correcao_fuso_6a c
WHERE c.tabela = 'abastecimentos' AND c.registro_id = a.id AND a.registrado_em = c.registrado_em_antes;

UPDATE ocorrencias o SET registrado_em = c.registrado_em_antes + INTERVAL '3 hours'
FROM correcao_fuso_6a c
WHERE c.tabela = 'ocorrencias' AND c.registro_id = o.id AND o.registrado_em = c.registrado_em_antes;

-- Confira os números mostrados pelos UPDATEs (devem bater com a contagem "corrigir_mais_3h").
-- Se baterem: COMMIT;   Se não: ROLLBACK;
*/

-- DESFAZER o PASSO 2 (se precisar):
/*
BEGIN;
UPDATE abastecimentos a SET registrado_em = c.registrado_em_antes
FROM correcao_fuso_6a c
WHERE c.tabela = 'abastecimentos' AND c.registro_id = a.id AND a.registrado_em = c.registrado_em_antes + INTERVAL '3 hours';
UPDATE ocorrencias o SET registrado_em = c.registrado_em_antes
FROM correcao_fuso_6a c
WHERE c.tabela = 'ocorrencias' AND c.registro_id = o.id AND o.registrado_em = c.registrado_em_antes + INTERVAL '3 hours';
COMMIT;
*/

-- =====================================================================
-- 19/10/2026 - Parte 3 (ocorrência com status) - RODAR ANTES DO DEPLOY
-- =====================================================================
-- CORRIGIDO 19/10: a coluna ocorrencias.status JÁ EXISTIA (varchar, default
-- 'pendente'::status_ocorrencia). O vocabulário da ocorrência é o do banco:
-- pendente, em_andamento, resolvida (decisão do coordenador). O antigo
-- "ADD COLUMN status ... DEFAULT 'aberta'" foi retirado daqui: NÃO rodar
-- SET DEFAULT 'aberta'. O backfill desta parte foi SUBSTITUÍDO pelo bloco
-- "Ocorrências: vocabulário do banco" no fim deste arquivo.

ALTER TABLE ocorrencias ADD COLUMN IF NOT EXISTS resolvida_em timestamp;

-- DESFAZER: voltar o código para antes da Parte 3 NÃO exige mexer no banco
-- (o código antigo funciona com a coluna nova). Não apague a coluna.

-- =====================================================================
-- 19/10/2026 - Parte 5 (segurança e bloqueios) - SÓ LEITURA, rodar ANTES do deploy
-- =====================================================================
-- Depois da Parte 5 a API recusa abrir diário quando o motorista:
--   a) não tem PIN de assinatura, ou
--   b) tem diário FECHADO aguardando assinatura.
-- Diários antigos (de antes da assinatura existir) podem estar como 'fechado' e
-- travar motoristas. Confira quem seria bloqueado hoje:
-- SELECT u.login, u.nome_completo,
--        (u.pin_assinatura_hash IS NOT NULL) AS tem_pin,
--        COUNT(d.id) FILTER (WHERE d.status = 'fechado') AS diarios_pendentes,
--        MIN(d.fechado_em) FILTER (WHERE d.status = 'fechado') AS pendente_mais_antigo
-- FROM usuarios u
-- LEFT JOIN diarios_bordo d ON d.usuario_id = u.id
-- WHERE CAST(u.perfil AS text) = 'motorista' AND u.ativo = true
-- GROUP BY u.login, u.nome_completo, u.pin_assinatura_hash
-- ORDER BY diarios_pendentes DESC, u.login;
-- Se aparecerem muitos pendentes antigos, me mande o resultado antes do deploy.

-- =====================================================================
-- 19/10/2026 - Parte 6B - CONSULTAS PARA O PLANO (só leitura; mande os resultados)
-- =====================================================================
-- C1) Colunas das tabelas de evento:
-- SELECT table_name, column_name, data_type, is_nullable, column_default
-- FROM information_schema.columns
-- WHERE table_schema = 'public'
--   AND table_name IN ('turnos','diarios_bordo','abastecimentos','ocorrencias','rastreamento_gps','checklist_respostas','veiculos')
-- ORDER BY table_name, ordinal_position;

-- C2) Índices e restrições únicas dessas tabelas:
-- SELECT tablename, indexname, indexdef
-- FROM pg_indexes
-- WHERE schemaname = 'public'
--   AND tablename IN ('turnos','diarios_bordo','abastecimentos','ocorrencias','rastreamento_gps','checklist_respostas')
-- ORDER BY tablename, indexname;

-- C3) Triggers:
-- SELECT event_object_table AS tabela, trigger_name, action_timing, event_manipulation, action_statement
-- FROM information_schema.triggers
-- WHERE trigger_schema = 'public'
-- ORDER BY 1, 2;

-- C4) Código das funções do banco (inclui as chamadas pelos triggers):
-- SELECT p.proname AS funcao, pg_get_functiondef(p.oid) AS definicao
-- FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
-- WHERE n.nspname = 'public' AND p.prokind = 'f'
-- ORDER BY 1;

-- C5) Valores dos enums (status de turno, diário, veículo...):
-- SELECT t.typname AS enum, e.enumlabel AS valor
-- FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid
-- ORDER BY t.typname, e.enumsortorder;

-- C6) Volume (para dimensionar o rastreamento em lote):
-- SELECT 'rastreamento_gps' AS tabela, COUNT(*) FROM rastreamento_gps
-- UNION ALL SELECT 'abastecimentos', COUNT(*) FROM abastecimentos
-- UNION ALL SELECT 'ocorrencias', COUNT(*) FROM ocorrencias
-- UNION ALL SELECT 'diarios_bordo', COUNT(*) FROM diarios_bordo;

-- =====================================================================
-- 19/10/2026 - Ocorrências: vocabulário do banco (pendente, em_andamento, resolvida)
-- RODAR ANTES DO DEPLOY do lote Parte 3 + Parte 5 + codigo. Idempotente; rodar nesta ordem.
-- O código antigo não grava status (usa o default 'pendente'), então tudo isto é compatível com ele.
-- =====================================================================
-- Conferência antes (só leitura):
-- SELECT status, COUNT(*) FROM ocorrencias GROUP BY status;

BEGIN;

-- 1) Normalização: só para o caso de algum código antigo ter gravado o vocabulário velho.
UPDATE ocorrencias SET status = 'pendente'  WHERE status = 'aberta';
UPDATE ocorrencias SET status = 'resolvida' WHERE status = 'concluida';

-- 2) Backfill da Parte 3 (status calculado pelas manutenções vinculadas).
--    (manutenção continua com aberta / em_andamento / concluida)
-- 2a) Alguma manutenção vinculada ainda não concluída -> em_andamento
UPDATE ocorrencias o SET status = 'em_andamento', resolvida_em = NULL
WHERE EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id = o.id AND m.status <> 'concluida');

-- 2b) Tem manutenção vinculada e TODAS concluídas -> resolvida (mantém resolvida_em se já houver)
UPDATE ocorrencias o SET status = 'resolvida',
       resolvida_em = COALESCE(o.resolvida_em,
                               (SELECT MAX(m.concluida_em) FROM manutencoes m WHERE m.ocorrencia_id = o.id))
WHERE EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id = o.id)
  AND NOT EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id = o.id AND m.status <> 'concluida');

-- 3) Por último: o banco passa a barrar valor fora do vocabulário (a coluna é varchar).
--    DROP antes do ADD para poder rodar de novo.
ALTER TABLE ocorrencias DROP CONSTRAINT IF EXISTS ck_ocorrencias_status;
ALTER TABLE ocorrencias ADD CONSTRAINT ck_ocorrencias_status
    CHECK (status IN ('pendente', 'em_andamento', 'resolvida'));

-- Confira: SELECT status, COUNT(*) FROM ocorrencias GROUP BY status;
-- Se estiver certo: COMMIT;   Se não: ROLLBACK;
-- (Se o ADD CONSTRAINT falhar, existe algum valor fora dos três: o SELECT acima mostra qual.)

-- DESFAZER (se precisar): ALTER TABLE ocorrencias DROP CONSTRAINT IF EXISTS ck_ocorrencias_status;

-- =====================================================================
-- 19/10/2026 - Parte 6B - SQL 1 de 2: COLUNAS NOVAS
-- NÃO RODAR AGORA. Ordem obrigatória:
--   1) deploy do lote (Passo 1 + "etapa 0 da 6B": consultas de abastecimento e
--      checklist com colunas explícitas). Com o código ANTIGO no ar, colunas novas
--      em abastecimentos/checklist_respostas quebram telas que liam "a.*" por posição;
--   2) rodar este bloco;
--   3) deploy do código da 6B.
-- Tudo aditivo e idempotente. Volume pequeno: sem risco de tempo.
-- =====================================================================
/*
BEGIN;

ALTER TABLE turnos
    ADD COLUMN IF NOT EXISTS aberto_recebido_em  timestamp,
    ADD COLUMN IF NOT EXISTS fechado_recebido_em timestamp,
    ADD COLUMN IF NOT EXISTS id_local_abertura   uuid,
    ADD COLUMN IF NOT EXISTS id_local_fechamento uuid,
    ADD COLUMN IF NOT EXISTS horario_suspeito    boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS motivo_suspeita     varchar(255);

ALTER TABLE diarios_bordo
    ADD COLUMN IF NOT EXISTS aberto_recebido_em    timestamp,
    ADD COLUMN IF NOT EXISTS fechado_recebido_em   timestamp,
    ADD COLUMN IF NOT EXISTS id_local_abertura     uuid,
    ADD COLUMN IF NOT EXISTS id_local_fechamento   uuid,
    ADD COLUMN IF NOT EXISTS desvio_abertura_seg   integer,
    ADD COLUMN IF NOT EXISTS desvio_fechamento_seg integer,
    ADD COLUMN IF NOT EXISTS horario_suspeito      boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS motivo_suspeita       varchar(255);

ALTER TABLE abastecimentos
    ADD COLUMN IF NOT EXISTS recebido_em        timestamp,
    ADD COLUMN IF NOT EXISTS id_local           uuid,
    ADD COLUMN IF NOT EXISTS desvio_relogio_seg integer,
    ADD COLUMN IF NOT EXISTS horario_suspeito   boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS motivo_suspeita    varchar(255),
    -- APROVADO 19/10: quem registrou (usuário do token; no comboio não é o dono do diário). Antigos ficam nulos.
    ADD COLUMN IF NOT EXISTS registrado_por     uuid;

ALTER TABLE ocorrencias
    ADD COLUMN IF NOT EXISTS recebido_em        timestamp,
    ADD COLUMN IF NOT EXISTS id_local           uuid,
    ADD COLUMN IF NOT EXISTS desvio_relogio_seg integer,
    ADD COLUMN IF NOT EXISTS horario_suspeito   boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS motivo_suspeita    varchar(255);

ALTER TABLE rastreamento_gps
    ADD COLUMN IF NOT EXISTS recebido_em        timestamp,
    ADD COLUMN IF NOT EXISTS id_local           uuid,
    ADD COLUMN IF NOT EXISTS desvio_relogio_seg integer,
    ADD COLUMN IF NOT EXISTS horario_suspeito   boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS motivo_suspeita    varchar(255);

ALTER TABLE checklist_respostas
    ADD COLUMN IF NOT EXISTS recebido_em timestamp;

-- Reenvio sem duplicar: índice único parcial por id_local
CREATE UNIQUE INDEX IF NOT EXISTS uq_turnos_id_local_abertura    ON turnos (id_local_abertura)          WHERE id_local_abertura   IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_turnos_id_local_fechamento  ON turnos (id_local_fechamento)        WHERE id_local_fechamento IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_diarios_id_local_abertura   ON diarios_bordo (id_local_abertura)   WHERE id_local_abertura   IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_diarios_id_local_fechamento ON diarios_bordo (id_local_fechamento) WHERE id_local_fechamento IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_abastecimentos_id_local     ON abastecimentos (id_local)           WHERE id_local IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_ocorrencias_id_local        ON ocorrencias (id_local)              WHERE id_local IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_rastreamento_id_local       ON rastreamento_gps (id_local)         WHERE id_local IS NOT NULL;

COMMIT;
*/

-- =====================================================================
-- 19/10/2026 - Parte 6B - SQL 2 de 2: TRIGGERS NOVOS
-- NÃO RODAR antes da revisão do coordenador. Independe do deploy (o código funciona
-- com o trigger antigo ou com o novo).
-- fn_fecha_diario:
--   a) medidor_atual só muda se este for o fechamento MAIS RECENTE do veículo (por fechado_em);
--   b) status: inativo continua inativo; manutenção não concluída -> em_manutencao; senão disponivel;
--   c) histórico com o status real (antes e depois) e alterado_em = hora do evento (fechado_em).
-- fn_abre_diario: histórico com alterado_em = aberto_em (hora do evento). O status de antes já era o real.
-- =====================================================================
/*
CREATE OR REPLACE FUNCTION public.fn_fecha_diario()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_fechado_em    timestamp := COALESCE(NEW.fechado_em, NOW());
    v_status_antes  status_veiculo;
    v_status_depois status_veiculo;
    v_mais_recente  boolean;
BEGIN
    IF NEW.status = 'fechado' AND OLD.status = 'aberto' AND NEW.medidor_final IS NOT NULL THEN

        NEW.medidor_percorrido = NEW.medidor_final - NEW.medidor_inicial;

        SELECT status INTO v_status_antes FROM veiculos WHERE id = NEW.veiculo_id FOR UPDATE;

        -- a) um fechamento antigo que chega atrasado (fila offline) não faz o medidor voltar
        v_mais_recente := NOT EXISTS (
            SELECT 1 FROM diarios_bordo d
            WHERE d.veiculo_id = NEW.veiculo_id
              AND d.id <> NEW.id
              AND d.status IN ('fechado', 'assinado')
              AND d.fechado_em > v_fechado_em
        );

        -- b) status real do veículo depois do fechamento
        IF v_status_antes = 'inativo' THEN
            v_status_depois := 'inativo';
        ELSIF EXISTS (SELECT 1 FROM manutencoes m
                      WHERE m.veiculo_id = NEW.veiculo_id AND m.status <> 'concluida') THEN
            v_status_depois := 'em_manutencao';
        ELSE
            v_status_depois := 'disponivel';
        END IF;

        UPDATE veiculos
        SET medidor_atual = CASE WHEN v_mais_recente THEN NEW.medidor_final ELSE medidor_atual END,
            status        = v_status_depois,
            atualizado_em = NOW()
        WHERE id = NEW.veiculo_id;

        -- c) histórico com o status real e a hora do evento
        INSERT INTO veiculo_status_historico (veiculo_id, status_antes, status_depois, alterado_por, alterado_em)
        VALUES (NEW.veiculo_id, v_status_antes, v_status_depois, NEW.usuario_id, v_fechado_em);

    END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.fn_abre_diario()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_status_antes status_veiculo;
BEGIN
    SELECT status INTO v_status_antes FROM veiculos WHERE id = NEW.veiculo_id FOR UPDATE;

    UPDATE veiculos
    SET status        = 'em_uso',
        atualizado_em = NOW()
    WHERE id = NEW.veiculo_id;

    INSERT INTO veiculo_status_historico (veiculo_id, status_antes, status_depois, alterado_por, alterado_em)
    VALUES (NEW.veiculo_id, v_status_antes, 'em_uso', NEW.usuario_id, COALESCE(NEW.aberto_em, NOW()));

    RETURN NEW;
END;
$function$;
*/

-- ROLLBACK dos triggers: texto ORIGINAL (consulta C4 de 19/10). Rodar só para voltar.
/*
CREATE OR REPLACE FUNCTION public.fn_abre_diario()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_status_antes status_veiculo;
BEGIN
    SELECT status INTO v_status_antes FROM veiculos WHERE id = NEW.veiculo_id;

    UPDATE veiculos
    SET status        = 'em_uso',
        atualizado_em = NOW()
    WHERE id = NEW.veiculo_id;

    INSERT INTO veiculo_status_historico (veiculo_id, status_antes, status_depois, alterado_por)
    VALUES (NEW.veiculo_id, v_status_antes, 'em_uso', NEW.usuario_id);

    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.fn_fecha_diario()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.status = 'fechado' AND OLD.status = 'aberto' AND NEW.medidor_final IS NOT NULL THEN

        NEW.medidor_percorrido = NEW.medidor_final - NEW.medidor_inicial;

        UPDATE veiculos
        SET medidor_atual  = NEW.medidor_final,
            status         = 'disponivel',
            atualizado_em  = NOW()
        WHERE id = NEW.veiculo_id;

        INSERT INTO veiculo_status_historico (veiculo_id, status_antes, status_depois, alterado_por)
        VALUES (NEW.veiculo_id, 'em_uso', 'disponivel', NEW.usuario_id);

    END IF;
    RETURN NEW;
END;
$function$;
*/

-- =====================================================================
-- 19/10/2026 - Parte 9 (fotos no banco) - etapa 2
-- NÃO RODAR antes de o coordenador aprovar ESTE texto. Rodar ANTES do deploy do código da etapa 2
-- (o POST /fotos grava nestas colunas). Aditivo: nenhum código antigo usa a tabela fotos (está vazia).
-- PostgreSQL 18 (consulta Q3): ADD VALUE em enum é aceito.
-- =====================================================================

-- PASSO 1: valores novos do enum. Rodar SOZINHO, fora de BEGIN/COMMIT
-- (um valor novo de enum não pode ser usado na mesma transação em que foi criado).
/*
ALTER TYPE tipo_foto ADD VALUE IF NOT EXISTS 'cupom_fiscal';
ALTER TYPE tipo_foto ADD VALUE IF NOT EXISTS 'bomba_comboio';
*/

-- PASSO 2: colunas novas. caminho_arquivo vira opcional (fica para armazenamento externo no futuro).
-- tirada_em (já existe) passa a ser a hora em que o celular tirou a foto; a API grava explícito em UTC.
-- idx_fotos_diario já existe (consulta Q1): não é recriado.
/*
BEGIN;

ALTER TABLE fotos
    ALTER COLUMN caminho_arquivo DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS conteudo           bytea,
    ADD COLUMN IF NOT EXISTS tipo_mime          varchar(20),
    ADD COLUMN IF NOT EXISTS tamanho_bytes      integer,
    ADD COLUMN IF NOT EXISTS hash_sha256        char(64),
    ADD COLUMN IF NOT EXISTS id_local           uuid,
    ADD COLUMN IF NOT EXISTS recebida_em        timestamp,
    ADD COLUMN IF NOT EXISTS registrado_por     uuid,
    ADD COLUMN IF NOT EXISTS abastecimento_id   uuid REFERENCES abastecimentos(id),
    ADD COLUMN IF NOT EXISTS desvio_relogio_seg integer,
    ADD COLUMN IF NOT EXISTS horario_suspeito   boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS motivo_suspeita    varchar(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fotos_id_local    ON fotos (id_local) WHERE id_local IS NOT NULL;
CREATE INDEX        IF NOT EXISTS idx_fotos_abastecimento ON fotos (abastecimento_id);

COMMIT;
*/

-- Conferência (só leitura):
-- SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'fotos' ORDER BY ordinal_position;
-- SELECT e.enumlabel FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid WHERE t.typname = 'tipo_foto' ORDER BY e.enumsortorder;

-- ROLLBACK (só se precisar voltar; apaga as fotos gravadas pela API):
/*
BEGIN;
DROP INDEX IF EXISTS idx_fotos_abastecimento;
DROP INDEX IF EXISTS uq_fotos_id_local;
ALTER TABLE fotos
    DROP COLUMN IF EXISTS motivo_suspeita, DROP COLUMN IF EXISTS horario_suspeito, DROP COLUMN IF EXISTS desvio_relogio_seg,
    DROP COLUMN IF EXISTS abastecimento_id, DROP COLUMN IF EXISTS registrado_por, DROP COLUMN IF EXISTS recebida_em,
    DROP COLUMN IF EXISTS id_local, DROP COLUMN IF EXISTS hash_sha256, DROP COLUMN IF EXISTS tamanho_bytes,
    DROP COLUMN IF EXISTS tipo_mime, DROP COLUMN IF EXISTS conteudo;
-- caminho_arquivo volta a ser obrigatório só se nenhuma linha estiver sem caminho:
-- ALTER TABLE fotos ALTER COLUMN caminho_arquivo SET NOT NULL;
COMMIT;
-- Os valores 'cupom_fiscal' e 'bomba_comboio' do enum NÃO saem (o PostgreSQL não remove valor de enum); ficam sem uso.
*/

-- =====================================================================
-- Foto de perfil (20/10, aprovado). Rodar ANTES do deploy da normalização da foto de perfil.
-- Aditiva: varchar(255) -> text não perde dados e o código anterior continua funcionando.
-- Pode rodar de novo sem efeito (a coluna já fica text).
-- =====================================================================
ALTER TABLE usuarios ALTER COLUMN foto_perfil TYPE text;

-- Conferência (só leitura): deve mostrar data_type = text e character_maximum_length vazio.
-- SELECT data_type, character_maximum_length FROM information_schema.columns
-- WHERE table_name = 'usuarios' AND column_name = 'foto_perfil';
-- ROLLBACK (só se nenhuma foto passar de 255 caracteres, o que não vai acontecer depois do uso):
-- ALTER TABLE usuarios ALTER COLUMN foto_perfil TYPE varchar(255);
