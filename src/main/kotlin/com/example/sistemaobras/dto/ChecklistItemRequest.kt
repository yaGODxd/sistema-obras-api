package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank

data class ChecklistItemRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val templateId: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val descricao: String,
    val obrigatorio: Boolean = true,
    val ordem: Int = 0
)