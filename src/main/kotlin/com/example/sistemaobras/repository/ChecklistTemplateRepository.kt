package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.ChecklistTemplate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChecklistTemplateRepository : JpaRepository<ChecklistTemplate, UUID> {
    @Query(value = "SELECT * FROM checklist_templates WHERE ativo = true ORDER BY nome", nativeQuery = true)
    fun findAtivos(): List<ChecklistTemplate>

    @Query(value = "SELECT * FROM checklist_templates WHERE ativo = true LIMIT 1", nativeQuery = true)
    fun findTemplateAtivo(): ChecklistTemplate?
}