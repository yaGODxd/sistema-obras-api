-- Migrações da API SGFE.
-- Regras: idempotente (IF NOT EXISTS), aditiva e compatível com o código anterior.
-- Ordem em produção: o Yago roda o SQL e DEPOIS faz o deploy do código.
-- Backfill (correção de dados existentes) fica em bloco separado e comentado.

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
-- O código da Parte 3 lê e grava ocorrencias.status e ocorrencias.resolvida_em.
-- Sem estas colunas, as telas de ocorrência dão erro. Compatível com o código
-- anterior: ele não usa as colunas e o banco preenche status com 'aberta'.
-- Texto (varchar), NÃO enum do Postgres (lição do status_veiculo).
-- Valores: aberta, em_andamento, concluida, descartada (a API valida).

ALTER TABLE ocorrencias ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'aberta';
ALTER TABLE ocorrencias ADD COLUMN IF NOT EXISTS resolvida_em timestamp;

-- Conferência (só leitura):
-- SELECT status, COUNT(*) FROM ocorrencias GROUP BY status;

-- BACKFILL das ocorrências que já existem. NÃO RODE sem autorização do texto exato.
-- Pode rodar antes ou depois do deploy (depois do ALTER). Rodar duas vezes dá o mesmo resultado.
-- Ocorrências sem manutenção ficam 'aberta'.
/*
BEGIN;

-- Tem manutenção vinculada ainda não concluída -> em_andamento
UPDATE ocorrencias o SET status = 'em_andamento', resolvida_em = NULL
WHERE EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id = o.id AND m.status <> 'concluida');

-- Tem manutenção vinculada e TODAS concluídas -> concluida (resolvida_em = última conclusão)
UPDATE ocorrencias o SET status = 'concluida',
       resolvida_em = (SELECT MAX(m.concluida_em) FROM manutencoes m WHERE m.ocorrencia_id = o.id)
WHERE EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id = o.id)
  AND NOT EXISTS (SELECT 1 FROM manutencoes m WHERE m.ocorrencia_id = o.id AND m.status <> 'concluida');

-- Confira: SELECT status, COUNT(*) FROM ocorrencias GROUP BY status;
-- Se estiver certo: COMMIT;   Se não: ROLLBACK;
*/

-- DESFAZER: voltar o código para antes da Parte 3 NÃO exige mexer no banco
-- (o código antigo funciona com as colunas novas). Não apague as colunas.
