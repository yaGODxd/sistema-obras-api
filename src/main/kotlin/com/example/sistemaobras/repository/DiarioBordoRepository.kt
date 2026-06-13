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
}