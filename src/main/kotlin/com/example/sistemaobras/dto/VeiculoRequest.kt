package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

data class VeiculoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val tipoId: String,
    val placa: String?,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val descricao: String,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorAtual: Double = 0.0,
    val marca: String? = null,
    val modelo: String? = null,
    val ano: Int? = null,
    val cor: String? = null,
    val renavam: String? = null,
    val chassi: String? = null,
    val comboio: Boolean = false
)