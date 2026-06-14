package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.ManutencaoPeca
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ManutencaoPecaRepository : JpaRepository<ManutencaoPeca, UUID> {
    fun findByManutencaoId(manutencaoId: UUID): List<ManutencaoPeca>
}