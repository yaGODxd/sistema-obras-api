package com.example.sistemaobras.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class PecaRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val nomePeca: String,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val codigoPeca: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val marcaPeca: String? = null,
    @field:Min(value = 1, message = MensagensValidacao.MINIMO)
    val quantidade: Int,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val valorUnitario: Double
)