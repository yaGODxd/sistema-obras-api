package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class VeiculoRequest(
    // Obrigatório no cadastro (conferido no VeiculoService); na edição é opcional e ignorado.
    val tipoId: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val placa: String?,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val descricao: String,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorAtual: Double = 0.0,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val marca: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val modelo: String? = null,
    val ano: Int? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val cor: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val renavam: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val chassi: String? = null,
    val comboio: Boolean = false
)