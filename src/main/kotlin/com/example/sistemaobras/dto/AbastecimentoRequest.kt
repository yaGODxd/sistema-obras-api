package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class AbastecimentoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val loginMotorista: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val tipoCombustivel: String,
    @field:Positive(message = MensagensValidacao.MAIOR_QUE_ZERO)
    val litros: Double,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val valorTotal: Double,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val posto: String? = null,
    val veiculoAbastecidoId: String? = null,
    val registradoEm: Long? = null
)