package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class ChecklistRespostaResponse(
    val id: UUID?,
    val itemId: UUID?,
    val descricao: String,
    val obrigatorio: Boolean,
    val ok: Boolean,
    val observacao: String?,
    val registradoEm: LocalDateTime?
)