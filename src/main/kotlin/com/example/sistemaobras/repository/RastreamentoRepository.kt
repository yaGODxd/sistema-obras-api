package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.RastreamentoGps
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface RastreamentoRepository : JpaRepository<RastreamentoGps, UUID> {

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO rastreamento_gps (diario_id, latitude, longitude, velocidade, registrado_em)
            SELECT d.id, :latitude, :longitude, :velocidade, NOW()
            FROM diarios_bordo d
            INNER JOIN usuarios u ON u.id = d.usuario_id
            WHERE u.login = :login AND d.status = 'aberto'
        """,
        nativeQuery = true
    )
    fun inserirPonto(
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("latitude") latitude: Double,
        @org.springframework.data.repository.query.Param("longitude") longitude: Double,
        @org.springframework.data.repository.query.Param("velocidade") velocidade: Double?
    )

    @Query(
        value = """
            SELECT * FROM rastreamento_gps
            WHERE diario_id = CAST(:diarioId AS uuid)
            ORDER BY registrado_em ASC
        """,
        nativeQuery = true
    )
    fun findByDiarioId(
        @org.springframework.data.repository.query.Param("diarioId") diarioId: String
    ): List<RastreamentoGps>

    @Query(
        value = """
            SELECT DISTINCT ON (u.login)
                u.login,
                u.nome_completo,
                r.latitude,
                r.longitude,
                r.registrado_em,
                v.descricao as veiculo_descricao
            FROM rastreamento_gps r
            INNER JOIN diarios_bordo d ON d.id = r.diario_id AND d.status = 'aberto'
            INNER JOIN usuarios u ON u.id = d.usuario_id
            INNER JOIN veiculos v ON v.id = d.veiculo_id
            ORDER BY u.login, r.registrado_em DESC
        """,
        nativeQuery = true
    )
    fun findUltimasPosicoes(): List<Array<Any>>
}