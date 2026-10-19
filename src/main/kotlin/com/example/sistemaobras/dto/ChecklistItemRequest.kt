package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChecklistItemRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val templateId: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val descricao: String,
    val obrigatorio: Boolean = true,
    val ordem: Int = 0
)