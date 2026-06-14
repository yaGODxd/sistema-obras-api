package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Manutencao
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ManutencaoRepository : JpaRepository<Manutencao, UUID> {

    fun findByVeiculoId(veiculoId: UUID): List<Manutencao>

    fun findByOcorrenciaId(ocorrenciaId: UUID): List<Manutencao>

    @Query("""
        SELECT m.* FROM manutencoes m
        WHERE m.status != 'concluida'
        ORDER BY m.aberta_em DESC
    """, nativeQuery = true)
    fun findAbertas(): List<Manutencao>
}