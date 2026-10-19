package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class AbrirDiarioRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val loginMotorista: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val veiculoId: String,
    val motivoUsoId: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val destino: String? = null,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorInicial: Double,
    val observacao: String? = null
)