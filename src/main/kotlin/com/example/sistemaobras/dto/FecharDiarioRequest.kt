package com.example.sistemaobras.dto

import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class FecharDiarioRequest(
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorFinal: Double,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val observacaoFechamento: String? = null
)