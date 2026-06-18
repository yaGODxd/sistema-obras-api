package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Mecanico
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface MecanicoRepository : JpaRepository<Mecanico, UUID> {

    @Query(value = "SELECT * FROM mecanicos WHERE ativo = true ORDER BY nome", nativeQuery = true)
    fun findAtivos(): List<Mecanico>

    @Modifying
    @Transactional
    @Query(value = "UPDATE mecanicos SET ativo = :ativo WHERE id = CAST(:id AS uuid)", nativeQuery = true)
    fun alterarAtivo(
        @org.springframework.data.repository.query.Param("id") id: String,
        @org.springframework.data.repository.query.Param("ativo") ativo: Boolean
    )

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE mecanicos SET
                nome = :nome,
                telefone = :telefone,
                especialidade = :especialidade
            WHERE id = CAST(:id AS uuid)
        """,
        nativeQuery = true
    )
    fun atualizar(
        @org.springframework.data.repository.query.Param("id") id: String,
        @org.springframework.data.repository.query.Param("nome") nome: String,
        @org.springframework.data.repository.query.Param("telefone") telefone: String?,
        @org.springframework.data.repository.query.Param("especialidade") especialidade: String?
    )
}