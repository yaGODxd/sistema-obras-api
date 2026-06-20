package com.example.sistemaobras.dto

import java.util.UUID

data class ChecklistTemplateResponse(
    val id: UUID?,
    val nome: String,
    val ativo: Boolean,
    val itens: List<ChecklistItemResponse> = emptyList()
)