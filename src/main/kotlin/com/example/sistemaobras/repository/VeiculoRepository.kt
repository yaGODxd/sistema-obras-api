package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Veiculo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface VeiculoRepository : JpaRepository<Veiculo, UUID> {

    @Query(
        value = "SELECT * FROM veiculos WHERE status != 'inativo' ORDER BY descricao",
        nativeQuery = true
    )
    fun findAllAtivos(): List<Veiculo>

    @Query(
        value = "SELECT * FROM veiculos WHERE status = 'disponivel' ORDER BY descricao",
        nativeQuery = true
    )
    fun findDisponiveis(): List<Veiculo>

    @Query(
        value = """
            SELECT 
                v.id, tv.nome as tipo_nome, tv.medidor as tipo_medidor,
                v.placa, v.descricao, v.status, v.medidor_atual,
                u.id as motorista_id, u.nome_completo as motorista_nome,
                u.login as motorista_login, d.aberto_em as diario_aberto_em
            FROM veiculos v
            INNER JOIN tipo_veiculo tv ON tv.id = v.tipo_id
            INNER JOIN diarios_bordo d ON d.veiculo_id = v.id AND d.status = 'aberto'
            INNER JOIN usuarios u ON u.id = d.usuario_id
            ORDER BY d.aberto_em DESC
        """,
        nativeQuery = true
    )
    fun findEmUso(): List<Array<Any>>

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO veiculos (tipo_id, placa, descricao, status, medidor_atual)
            VALUES (CAST(:tipoId AS uuid), :placa, :descricao, 'disponivel', :medidorAtual)
        """,
        nativeQuery = true
    )
    fun inserirVeiculo(
        @org.springframework.data.repository.query.Param("tipoId") tipoId: String,
        @org.springframework.data.repository.query.Param("placa") placa: String?,
        @org.springframework.data.repository.query.Param("descricao") descricao: String,
        @org.springframework.data.repository.query.Param("medidorAtual") medidorAtual: Double
    )

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE veiculos SET
                placa = :placa,
                descricao = :descricao,
                atualizado_em = NOW()
            WHERE id = CAST(:id AS uuid)
        """,
        nativeQuery = true
    )
    fun atualizarVeiculo(
        @org.springframework.data.repository.query.Param("id") id: String,
        @org.springframework.data.repository.query.Param("placa") placa: String?,
        @org.springframework.data.repository.query.Param("descricao") descricao: String
    )

    @Modifying
    @Transactional
    @Query(
        value = "UPDATE veiculos SET status = 'inativo', atualizado_em = NOW() WHERE id = CAST(:id AS uuid)",
        nativeQuery = true
    )
    fun inativarVeiculo(
        @org.springframework.data.repository.query.Param("id") id: String
    )
}