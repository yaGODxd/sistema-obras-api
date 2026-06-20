package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "checklist_respostas")
data class ChecklistResposta(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "diario_id", nullable = false)
    val diarioId: UUID? = null,

    @Column(name = "item_id", nullable = false)
    val itemId: UUID? = null,

    @Column(name = "ok", nullable = false)
    val ok: Boolean = false,

    @Column(name = "observacao")
    val observacao: String? = null,

    @Column(name = "resposta")
    val resposta: String? = null,

    @Column(name = "sincronizado")
    val sincronizado: Boolean = false,

    @Column(name = "registrado_em", nullable = false)
    val registradoEm: java.time.LocalDateTime = java.time.LocalDateTime.now()
)