package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.DiarioBordo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Parte 6B: colunas para achar/validar o diário de um evento (ver índices em buscarParaEvento).
internal const val COLUNAS_DIARIO_EVENTO = """
    SELECT d.id, u.login, CAST(d.status AS text), d.aberto_em, d.fechado_em, d.veiculo_id,
           d.id_local_abertura, d.id_local_fechamento, d.medidor_inicial
    FROM diarios_bordo d
    INNER JOIN usuarios u ON u.id = d.usuario_id
"""

@Repository
interface DiarioBordoRepository : JpaRepository<DiarioBordo, UUID> {

    @Query(
        value = """
            SELECT COUNT(*) FROM diarios_bordo d
            INNER JOIN usuarios u ON u.id = d.usuario_id
            WHERE u.login = :login AND d.status = 'aberto'
        """,
        nativeQuery = true
    )
    fun temDiarioAberto(
        @org.springframework.data.repository.query.Param("login") login: String
    ): Long

    @Query(
        value = """
            SELECT d.* FROM diarios_bordo d
            INNER JOIN usuarios u ON u.id = d.usuario_id
            WHERE u.login = :login AND d.status = 'aberto'
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findDiarioAberto(
        @org.springframework.data.repository.query.Param("login") login: String
    ): DiarioBordo?

    // Parte 6B: aberto_em = hora do evento (celular); aberto_recebido_em = hora do servidor.
    @Modifying
    @Transactional
    @Query(
        value = """
    INSERT INTO diarios_bordo (turno_id, usuario_id, veiculo_id, motivo_uso_id, destino, medidor_inicial, status,
                               aberto_em, aberto_recebido_em, id_local_abertura, desvio_abertura_seg,
                               horario_suspeito, motivo_suspeita)
    SELECT
        t.id,
        u.id,
        CAST(:veiculoId AS uuid),
        CAST(:motivoUsoId AS uuid),
        :destino,
        :medidorInicial,
        'aberto',
        :abertoEm,
        :recebidoEm,
        CAST(:idLocal AS uuid),
        CAST(:desvio AS integer),
        :suspeito,
        CAST(:motivo AS varchar)
    FROM usuarios u
    INNER JOIN turnos t ON t.usuario_id = u.id AND t.status = 'aberto'
    WHERE u.login = :login
""",
        nativeQuery = true
    )
    fun abrirDiario(
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("veiculoId") veiculoId: String,
        @org.springframework.data.repository.query.Param("motivoUsoId") motivoUsoId: String?,
        @org.springframework.data.repository.query.Param("destino") destino: String?,
        @org.springframework.data.repository.query.Param("medidorInicial") medidorInicial: Double,
        @org.springframework.data.repository.query.Param("abertoEm") abertoEm: java.time.LocalDateTime,
        @org.springframework.data.repository.query.Param("recebidoEm") recebidoEm: java.time.LocalDateTime,
        @org.springframework.data.repository.query.Param("idLocal") idLocal: String?,
        @org.springframework.data.repository.query.Param("desvio") desvio: Int?,
        @org.springframework.data.repository.query.Param("suspeito") suspeito: Boolean,
        @org.springframework.data.repository.query.Param("motivo") motivo: String?
    )

    // Parte 6B: fechado_em = hora do evento. Só fecha se ainda estiver aberto (devolve 0 se não).
    // O trigger fn_fecha_diario calcula o percorrido e atualiza o veículo.
    @Modifying
    @Transactional
    @Query(
        value = """
    UPDATE diarios_bordo SET
        status = 'fechado',
        medidor_final = :medidorFinal,
        medidor_percorrido = :medidorFinal - medidor_inicial,
        observacao_fechamento = :observacao,
        fechado_em = :fechadoEm,
        fechado_recebido_em = :recebidoEm,
        id_local_fechamento = CAST(:idLocal AS uuid),
        desvio_fechamento_seg = CAST(:desvio AS integer),
        horario_suspeito = horario_suspeito OR :suspeito,
        motivo_suspeita = CASE
            WHEN CAST(:motivo AS varchar) IS NULL THEN motivo_suspeita
            WHEN motivo_suspeita IS NULL THEN CAST(:motivo AS varchar)
            ELSE LEFT(motivo_suspeita || ',' || CAST(:motivo AS varchar), 255)
        END
    WHERE id = CAST(:id AS uuid) AND status = 'aberto'
""",
        nativeQuery = true
    )
    fun fecharDiario(
        @org.springframework.data.repository.query.Param("id") id: String,
        @org.springframework.data.repository.query.Param("medidorFinal") medidorFinal: Double,
        @org.springframework.data.repository.query.Param("observacao") observacao: String?,
        @org.springframework.data.repository.query.Param("fechadoEm") fechadoEm: java.time.LocalDateTime,
        @org.springframework.data.repository.query.Param("recebidoEm") recebidoEm: java.time.LocalDateTime,
        @org.springframework.data.repository.query.Param("idLocal") idLocal: String?,
        @org.springframework.data.repository.query.Param("desvio") desvio: Int?,
        @org.springframework.data.repository.query.Param("suspeito") suspeito: Boolean,
        @org.springframework.data.repository.query.Param("motivo") motivo: String?
    ): Int

    // ---------- Parte 6B: diário de um evento ----------
    // Colunas: 0 id, 1 login do dono, 2 status, 3 aberto_em, 4 fechado_em, 5 veiculo_id,
    //          6 id_local_abertura, 7 id_local_fechamento, 8 medidor_inicial

    @Query(value = COLUNAS_DIARIO_EVENTO + " WHERE d.id = CAST(:id AS uuid)", nativeQuery = true)
    fun buscarParaEvento(@org.springframework.data.repository.query.Param("id") id: String): List<Array<Any?>>

    @Query(value = COLUNAS_DIARIO_EVENTO + " WHERE d.id_local_abertura = CAST(:idLocal AS uuid)", nativeQuery = true)
    fun buscarPorIdLocalAbertura(@org.springframework.data.repository.query.Param("idLocal") idLocal: String): List<Array<Any?>>

    @Query(value = COLUNAS_DIARIO_EVENTO + " WHERE d.id_local_fechamento = CAST(:idLocal AS uuid)", nativeQuery = true)
    fun buscarPorIdLocalFechamento(@org.springframework.data.repository.query.Param("idLocal") idLocal: String): List<Array<Any?>>

    // Diário do veículo no horário do evento (tolerância de 5 min), aberto ou já fechado.
    @Query(
        value = COLUNAS_DIARIO_EVENTO + """
        WHERE d.veiculo_id = CAST(:veiculoId AS uuid)
          AND d.aberto_em <= CAST(:instante AS timestamp) + INTERVAL '5 minutes'
          AND (d.fechado_em IS NULL OR d.fechado_em >= CAST(:instante AS timestamp) - INTERVAL '5 minutes')
        ORDER BY d.aberto_em DESC
        LIMIT 1
    """,
        nativeQuery = true
    )
    fun buscarPorVeiculoNoHorario(
        @org.springframework.data.repository.query.Param("veiculoId") veiculoId: String,
        @org.springframework.data.repository.query.Param("instante") instante: java.time.LocalDateTime
    ): List<Array<Any?>>

    // O motorista dirigia um veículo de comboio no horário do evento?
    @Query(
        value = """
        SELECT COUNT(*) FROM diarios_bordo d
        INNER JOIN usuarios u ON u.id = d.usuario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        WHERE u.login = :login AND v.comboio = true
          AND d.aberto_em <= CAST(:instante AS timestamp) + INTERVAL '5 minutes'
          AND (d.fechado_em IS NULL OR d.fechado_em >= CAST(:instante AS timestamp) - INTERVAL '5 minutes')
    """,
        nativeQuery = true
    )
    fun contarDiarioComboioNoHorario(
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("instante") instante: java.time.LocalDateTime
    ): Long

    // Abertura do turno aberto do motorista (para conferir a janela do diário).
    @Query(
        value = """
        SELECT t.aberto_em FROM turnos t
        INNER JOIN usuarios u ON u.id = t.usuario_id
        WHERE u.login = :login AND t.status = 'aberto'
        LIMIT 1
    """,
        nativeQuery = true
    )
    fun aberturaDoTurnoAberto(@org.springframework.data.repository.query.Param("login") login: String): List<Any>

    @Query(
        value = """
        SELECT 
            d.id,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.nome_completo as motorista_nome,
            d.medidor_inicial,
            d.medidor_final,
            d.medidor_percorrido,
            d.destino,
            d.status,
            d.aberto_em,
            d.fechado_em,
            d.veiculo_id,
            CAST(tv.medidor AS text) AS tipo_medidor
        FROM diarios_bordo d
        INNER JOIN usuarios u ON u.id = d.usuario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        LEFT JOIN tipo_veiculo tv ON tv.id = v.tipo_id
        WHERE u.login = :login AND d.status = 'aberto'
        LIMIT 1
    """,
        nativeQuery = true
    )
    fun findDiarioAbertoDetalhado(
        @org.springframework.data.repository.query.Param("login") login: String
    ): List<Array<Any>>

    // Parte 6C: mês e ano contados no fuso de Brasília (aberto_em está em UTC).
    @Query(
        value = """
        SELECT 
            d.id,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.nome_completo as motorista_nome,
            d.medidor_inicial,
            d.medidor_final,
            d.medidor_percorrido,
            d.destino,
            d.status,
            d.aberto_em,
            d.fechado_em,
            d.veiculo_id,
            CAST(tv.medidor AS text) AS tipo_medidor
        FROM diarios_bordo d
        INNER JOIN usuarios u ON u.id = d.usuario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        LEFT JOIN tipo_veiculo tv ON tv.id = v.tipo_id
        WHERE (:veiculoId IS NULL OR d.veiculo_id = CAST(:veiculoId AS uuid))
        AND (
            :mes IS NULL OR (
                EXTRACT(MONTH FROM (d.aberto_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')) = :mes
                AND EXTRACT(YEAR FROM (d.aberto_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')) = :ano
            )
        )
        ORDER BY d.aberto_em DESC
    """,
        nativeQuery = true
    )
    fun findDiariosPorFiltro(
        @org.springframework.data.repository.query.Param("veiculoId") veiculoId: String?,
        @org.springframework.data.repository.query.Param("mes") mes: Int?,
        @org.springframework.data.repository.query.Param("ano") ano: Int?
    ): List<Array<Any>>

    // Detalhe de um diário (GET /diarios/{id}). Índices 0-12 iguais aos da lista;
    // 13 motivo_uso_id, 14 motivo_uso, 15 observacao_fechamento, 16 assinado_em, 17 login do motorista.
    @Query(
        value = """
        SELECT
            d.id,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.nome_completo as motorista_nome,
            d.medidor_inicial,
            d.medidor_final,
            d.medidor_percorrido,
            d.destino,
            d.status,
            d.aberto_em,
            d.fechado_em,
            d.veiculo_id,
            CAST(tv.medidor AS text) AS tipo_medidor,
            d.motivo_uso_id,
            mu.descricao AS motivo_uso,
            d.observacao_fechamento,
            d.assinado_em,
            u.login AS motorista_login
        FROM diarios_bordo d
        INNER JOIN usuarios u ON u.id = d.usuario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        LEFT JOIN tipo_veiculo tv ON tv.id = v.tipo_id
        LEFT JOIN motivos_uso mu ON mu.id = d.motivo_uso_id
        WHERE d.id = CAST(:id AS uuid)
    """,
        nativeQuery = true
    )
    fun buscarDetalhe(
        @org.springframework.data.repository.query.Param("id") id: String
    ): List<Array<Any?>>

    @Query(
        value = """
        SELECT 
            v.id as veiculo_id,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            EXTRACT(MONTH FROM (d.aberto_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')) as mes,
            EXTRACT(YEAR FROM (d.aberto_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')) as ano,
            COUNT(d.id) as total_diarios,
            MIN(d.medidor_inicial) as primeiro_medidor,
            MAX(COALESCE(d.medidor_final, d.medidor_inicial)) as ultimo_medidor,
            COALESCE(SUM(d.medidor_percorrido), 0) as total_percorrido
        FROM diarios_bordo d
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        WHERE (:ano IS NULL OR EXTRACT(YEAR FROM (d.aberto_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')) = :ano)
        AND (:mes IS NULL OR EXTRACT(MONTH FROM (d.aberto_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')) = :mes)
        AND (:veiculoId IS NULL OR d.veiculo_id = CAST(:veiculoId AS uuid))
        GROUP BY v.id, v.descricao, v.placa, EXTRACT(MONTH FROM (d.aberto_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')), EXTRACT(YEAR FROM (d.aberto_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo'))
        ORDER BY ano DESC, mes DESC, v.descricao
    """,
        nativeQuery = true
    )
    fun findResumoMensal(
        @org.springframework.data.repository.query.Param("veiculoId") veiculoId: String?,
        @org.springframework.data.repository.query.Param("mes") mes: Int?,
        @org.springframework.data.repository.query.Param("ano") ano: Int?
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT COUNT(*) FROM diarios_bordo d
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        WHERE v.id = CAST(:veiculoId AS uuid) AND d.status = 'aberto'
    """,
        nativeQuery = true
    )
    fun temDiarioAbertoPorVeiculo(
        @org.springframework.data.repository.query.Param("veiculoId") veiculoId: String
    ): Long

    // Diários fechados aguardando a assinatura do motorista.
    @Query(
        value = """
        SELECT COUNT(*) FROM diarios_bordo d
        INNER JOIN usuarios u ON u.id = d.usuario_id
        WHERE u.login = :login AND d.status = 'fechado'
    """,
        nativeQuery = true
    )
    fun contarPendentesDeAssinatura(
        @org.springframework.data.repository.query.Param("login") login: String
    ): Long
}
