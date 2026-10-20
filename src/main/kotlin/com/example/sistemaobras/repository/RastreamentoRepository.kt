package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.RastreamentoGps
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface RastreamentoRepository : JpaRepository<RastreamentoGps, UUID> {

    // Parte 6B: o diário já vem resolvido pelo service; registrado_em = hora do evento.
    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO rastreamento_gps (diario_id, latitude, longitude, velocidade, registrado_em, recebido_em,
                                          id_local, desvio_relogio_seg, horario_suspeito, motivo_suspeita)
            VALUES (CAST(:diarioId AS uuid), :latitude, :longitude, CAST(:velocidade AS numeric), :registradoEm, :recebidoEm,
                    CAST(:idLocal AS uuid), CAST(:desvio AS integer), :suspeito, CAST(:motivo AS varchar))
        """,
        nativeQuery = true
    )
    fun inserirPonto(
        @Param("diarioId") diarioId: String,
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
        @Param("velocidade") velocidade: Double?,
        @Param("registradoEm") registradoEm: LocalDateTime,
        @Param("recebidoEm") recebidoEm: LocalDateTime,
        @Param("idLocal") idLocal: String?,
        @Param("desvio") desvio: Int?,
        @Param("suspeito") suspeito: Boolean,
        @Param("motivo") motivo: String?
    )

    @Query(
        value = """
            SELECT * FROM rastreamento_gps
            WHERE diario_id = CAST(:diarioId AS uuid)
            ORDER BY registrado_em ASC
        """,
        nativeQuery = true
    )
    fun findByDiarioId(@Param("diarioId") diarioId: String): List<RastreamentoGps>

    // Posição mais recente de cada diário aberto, pela hora do EVENTO.
    // Parte 6B: ignora ponto com hora do evento mais de 5 min à frente do recebimento
    // (relógio adiantado não pode esconder a posição real).
    @Query(
        value = """
            SELECT DISTINCT ON (d.id)
                d.id            AS diario_id,
                u.login         AS login,
                u.nome_completo AS nome_completo,
                r.latitude      AS latitude,
                r.longitude     AS longitude,
                r.registrado_em AS registrado_em,
                v.descricao     AS veiculo_descricao,
                v.placa         AS veiculo_placa,
                d.destino       AS destino
            FROM rastreamento_gps r
            INNER JOIN diarios_bordo d ON d.id = r.diario_id AND d.status = 'aberto'
            INNER JOIN usuarios u ON u.id = d.usuario_id
            INNER JOIN veiculos v ON v.id = d.veiculo_id
            WHERE r.recebido_em IS NULL OR r.registrado_em <= r.recebido_em + INTERVAL '5 minutes'
            ORDER BY d.id, r.registrado_em DESC
        """,
        nativeQuery = true
    )
    fun findUltimasPosicoes(): List<Array<Any>>

    // Descarte de ponto repetido: outro ponto do MESMO diário a menos de 50 m e a menos de
    // 2 min (pela hora do evento). O LEAST/GREATEST evita erro do acos com pontos idênticos.
    @Query(
        value = """
        SELECT COUNT(*) FROM rastreamento_gps r
        WHERE r.diario_id = CAST(:diarioId AS uuid)
        AND r.registrado_em BETWEEN CAST(:instante AS timestamp) - INTERVAL '2 minutes'
                                AND CAST(:instante AS timestamp) + INTERVAL '2 minutes'
        AND (
            6371000 * acos(LEAST(1.0, GREATEST(-1.0,
                cos(radians(:latitude)) * cos(radians(r.latitude)) *
                cos(radians(r.longitude) - radians(:longitude)) +
                sin(radians(:latitude)) * sin(radians(r.latitude))
            )))
        ) < 50
    """,
        nativeQuery = true
    )
    fun contarProximosNoDiario(
        @Param("diarioId") diarioId: String,
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
        @Param("instante") instante: LocalDateTime
    ): Long

    // Reenvio: [0] id, [1] diario_id do ponto já gravado com este idLocal.
    @Query(
        value = "SELECT r.id, r.diario_id FROM rastreamento_gps r WHERE r.id_local = CAST(:idLocal AS uuid)",
        nativeQuery = true
    )
    fun buscarPorIdLocal(@Param("idLocal") idLocal: String): List<Array<Any?>>
}
