package com.example.sistemaobras.dto

data class ChecklistRespostaRequest(
    val diarioId: String,
    val respostas: List<ChecklistRespostaItemRequest>
)

data class ChecklistRespostaItemRequest(
    val itemId: String,
    val ok: Boolean,
    val observacao: String? = null
)