package com.example.sistemaobras.dto

data class ChecklistItemRequest(
    val templateId: String,
    val descricao: String,
    val obrigatorio: Boolean = true,
    val ordem: Int = 0
)