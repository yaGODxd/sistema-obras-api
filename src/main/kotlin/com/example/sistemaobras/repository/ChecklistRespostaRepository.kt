package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.ChecklistResposta
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChecklistRespostaRepository : JpaRepository<ChecklistResposta, UUID> {

    @Query(value = """
        SELECT cr.*, ci.descricao, ci.obrigatorio, ci.ordem
        FROM checklist_respostas cr
        INNER JOIN checklist_itens ci ON ci.id = cr.item_id
        WHERE cr.diario_id = CAST(:diarioId AS uuid)
        ORDER BY ci.ordem
    """, nativeQuery = true)
    fun findByDiarioId(
        @org.springframework.data.repository.query.Param("diarioId") diarioId: String
    ): List<Array<Any>>
}