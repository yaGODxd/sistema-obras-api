package com.example.sistemaobras.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

data class ChecklistRespostaRequest(
    val diarioId: String,
    @field:Valid
    val respostas: List<ChecklistRespostaItemRequest>,
    // Parte 6B: hora em que o checklist foi respondido no celular (opcional).
    val ocorridoEm: String? = null,
    val enviadoEm: String? = null
)

data class ChecklistRespostaItemRequest(
    val itemId: String,
    val ok: Boolean,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val observacao: String? = null
)