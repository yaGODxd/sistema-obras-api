package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Posto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface PostoRepository : JpaRepository<Posto, UUID> {

    @Query(value = "SELECT * FROM postos WHERE ativo = true ORDER BY nome", nativeQuery = true)
    fun findAtivos(): List<Posto>

    @Modifying
    @Transactional
    @Query(value = "UPDATE postos SET ativo = :ativo WHERE id = CAST(:id AS uuid)", nativeQuery = true)
    fun alterarAtivo(
        @org.springframework.data.repository.query.Param("id") id: String,
        @org.springframework.data.repository.query.Param("ativo") ativo: Boolean
    )

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE postos SET
                nome = :nome,
                endereco = :endereco,
                cidade = :cidade
            WHERE id = CAST(:id AS uuid)
        """,
        nativeQuery = true
    )
    fun atualizar(
        @org.springframework.data.repository.query.Param("id") id: String,
        @org.springframework.data.repository.query.Param("nome") nome: String,
        @org.springframework.data.repository.query.Param("endereco") endereco: String?,
        @org.springframework.data.repository.query.Param("cidade") cidade: String?
    )
}