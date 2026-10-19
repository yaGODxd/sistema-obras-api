package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

data class PecaRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val nomePeca: String,
    val codigoPeca: String? = null,
    val marcaPeca: String? = null,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val quantidade: Int,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val valorUnitario: Double
)