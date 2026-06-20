package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "checklist_templates")
data class ChecklistTemplate(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "nome", nullable = false)
    val nome: String = "",

    @Column(name = "ativo", nullable = false)
    val ativo: Boolean = true,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now()
)