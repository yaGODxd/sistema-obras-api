package com.example.sistemaobras.dto

import java.util.UUID

data class ChecklistItemResponse(
    val id: UUID?,
    val templateId: UUID?,
    val descricao: String,
    val obrigatorio: Boolean,
    val ordem: Int,
    val ativo: Boolean
)