package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Abastecimento
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface AbastecimentoRepository : JpaRepository<Abastecimento, UUID> {

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO abastecimentos (diario_id, tipo_combustivel, litros, valor_total, posto, registrado_em)
            SELECT d.id, :tipoCombustivel, :litros, :valorTotal, :posto, NOW()
            FROM diarios_bordo d
            INNER JOIN usuarios u ON u.id = d.usuario_id
            WHERE u.login = :login AND d.status = 'aberto'
        """,
        nativeQuery = true
    )
    fun inserirAbastecimento(
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("tipoCombustivel") tipoCombustivel: String,
        @org.springframework.data.repository.query.Param("litros") litros: Double,
        @org.springframework.data.repository.query.Param("valorTotal") valorTotal: Double,
        @org.springframework.data.repository.query.Param("posto") posto: String?
    )

    @Query(
        value = """
            SELECT a.* FROM abastecimentos a
            INNER JOIN diarios_bordo d ON d.id = a.diario_id
            INNER JOIN usuarios u ON u.id = d.usuario_id
            WHERE u.login = :login AND d.status = 'aberto'
            ORDER BY a.registrado_em DESC
        """,
        nativeQuery = true
    )
    fun findByDiarioAberto(
        @org.springframework.data.repository.query.Param("login") login: String
    ): List<Abastecimento>

    @Query(
        value = "SELECT a.* FROM abastecimentos a WHERE a.diario_id = CAST(:diarioId AS uuid) ORDER BY a.registrado_em DESC",
        nativeQuery = true
    )
    fun findByDiarioId(
        @org.springframework.data.repository.query.Param("diarioId") diarioId: String
    ): List<Abastecimento>

    @Query(
        value = """
        SELECT 
            a.*,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.nome_completo as motorista_nome
        FROM abastecimentos a
        INNER JOIN diarios_bordo d ON d.id = a.diario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        INNER JOIN usuarios u ON u.id = d.usuario_id
        WHERE v.id = CAST(:veiculoId AS uuid)
        ORDER BY a.registrado_em DESC
    """,
        nativeQuery = true
    )
    fun findByVeiculo(
        @org.springframework.data.repository.query.Param("veiculoId") veiculoId: String
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT 
            a.*,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.nome_completo as motorista_nome
        FROM abastecimentos a
        INNER JOIN diarios_bordo d ON d.id = a.diario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        INNER JOIN usuarios u ON u.id = d.usuario_id
        WHERE u.login = :login
        ORDER BY a.registrado_em DESC
    """,
        nativeQuery = true
    )
    fun findByMotorista(
        @org.springframework.data.repository.query.Param("login") login: String
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT 
            a.*,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.nome_completo as motorista_nome
        FROM abastecimentos a
        INNER JOIN diarios_bordo d ON d.id = a.diario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        INNER JOIN usuarios u ON u.id = d.usuario_id
        ORDER BY a.registrado_em DESC
    """,
        nativeQuery = true
    )
    fun findTodos(): List<Array<Any>>
}