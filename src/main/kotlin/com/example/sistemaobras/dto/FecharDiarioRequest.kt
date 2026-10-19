package com.example.sistemaobras.dto

import jakarta.validation.constraints.PositiveOrZero

data class FecharDiarioRequest(
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorFinal: Double,
    val observacaoFechamento: String? = null
)