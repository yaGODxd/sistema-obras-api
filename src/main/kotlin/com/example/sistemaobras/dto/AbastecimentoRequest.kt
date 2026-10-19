package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

data class AbastecimentoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val loginMotorista: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val tipoCombustivel: String,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val litros: Double,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val valorTotal: Double,
    val posto: String? = null,
    val veiculoAbastecidoId: String? = null,
    val registradoEm: Long? = null
)