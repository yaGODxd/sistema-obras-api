package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.ChecklistItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface ChecklistItemRepository : JpaRepository<ChecklistItem, UUID> {

    @Query(value = "SELECT * FROM checklist_itens WHERE template_id = CAST(:templateId AS uuid) AND ativo = true ORDER BY ordem", nativeQuery = true)
    fun findByTemplateId(
        @org.springframework.data.repository.query.Param("templateId") templateId: String
    ): List<ChecklistItem>

    @Modifying
    @Transactional
    @Query(value = "UPDATE checklist_itens SET ativo = :ativo WHERE id = CAST(:id AS uuid)", nativeQuery = true)
    fun alterarAtivo(
        @org.springframework.data.repository.query.Param("id") id: String,
        @org.springframework.data.repository.query.Param("ativo") ativo: Boolean
    )
}