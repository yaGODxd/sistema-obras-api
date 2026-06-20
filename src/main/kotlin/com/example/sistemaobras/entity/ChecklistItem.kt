package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "checklist_itens")
data class ChecklistItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "template_id", nullable = false)
    val templateId: UUID? = null,

    @Column(name = "descricao", nullable = false)
    val descricao: String = "",

    @Column(name = "obrigatorio", nullable = false)
    val obrigatorio: Boolean = true,

    @Column(name = "ordem", nullable = false)
    val ordem: Int = 0,

    @Column(name = "ativo", nullable = false)
    val ativo: Boolean = true
)