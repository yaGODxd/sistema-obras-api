package com.example.sistemaobras.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AtualizarStatusRequest(
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    @field:Pattern(regexp = "aberta|em_andamento|concluida", message = MensagensValidacao.STATUS_MANUTENCAO)
    val status: String,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val observacoes: String? = null
)