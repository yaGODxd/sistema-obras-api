package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.DiarioBordo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

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

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO diarios_bordo (turno_id, usuario_id, veiculo_id, motivo_uso_id, destino, medidor_inicial, status, aberto_em)
            SELECT 
                t.id,
                u.id,
                CAST(:veiculoId AS uuid),
                CAST(:motivoUsoId AS uuid),
                :destino,
                :medidorInicial,
                'aberto',
                NOW()
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
        @org.springframework.data.repository.query.Param("medidorInicial") medidorInicial: Double
    )

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE diarios_bordo SET
                status = 'fechado',
                medidor_final = :medidorFinal,
                medidor_percorrido = :medidorFinal - medidor_inicial,
                observacao_fechamento = :observacao,
                fechado_em = NOW()
            WHERE id = CAST(:id AS uuid)
        """,
        nativeQuery = true
    )
    fun fecharDiario(
        @org.springframework.data.repository.query.Param("id") id: String,
        @org.springframework.data.repository.query.Param("medidorFinal") medidorFinal: Double,
        @org.springframework.data.repository.query.Param("observacao") observacao: String?
    )

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
            d.fechado_em
        FROM diarios_bordo d
        INNER JOIN usuarios u ON u.id = d.usuario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        WHERE u.login = :login AND d.status = 'aberto'
        LIMIT 1
    """,
        nativeQuery = true
    )
    fun findDiarioAbertoDetalhado(
        @org.springframework.data.repository.query.Param("login") login: String
    ): List<Array<Any>>

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
            d.fechado_em
        FROM diarios_bordo d
        INNER JOIN usuarios u ON u.id = d.usuario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        WHERE (:veiculoId IS NULL OR d.veiculo_id = CAST(:veiculoId AS uuid))
        AND (
            :mes IS NULL OR (
                EXTRACT(MONTH FROM d.aberto_em) = :mes
                AND EXTRACT(YEAR FROM d.aberto_em) = :ano
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

    @Query(
        value = """
        SELECT 
            v.id as veiculo_id,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            EXTRACT(MONTH FROM d.aberto_em) as mes,
            EXTRACT(YEAR FROM d.aberto_em) as ano,
            COUNT(d.id) as total_diarios,
            MIN(d.medidor_inicial) as primeiro_medidor,
            MAX(COALESCE(d.medidor_final, d.medidor_inicial)) as ultimo_medidor,
            COALESCE(SUM(d.medidor_percorrido), 0) as total_percorrido
        FROM diarios_bordo d
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        WHERE (:ano IS NULL OR EXTRACT(YEAR FROM d.aberto_em) = :ano)
        AND (:mes IS NULL OR EXTRACT(MONTH FROM d.aberto_em) = :mes)
        AND (:veiculoId IS NULL OR d.veiculo_id = CAST(:veiculoId AS uuid))
        GROUP BY v.id, v.descricao, v.placa, EXTRACT(MONTH FROM d.aberto_em), EXTRACT(YEAR FROM d.aberto_em)
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
}