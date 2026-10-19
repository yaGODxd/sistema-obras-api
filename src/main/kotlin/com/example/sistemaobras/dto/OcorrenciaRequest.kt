package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class OcorrenciaRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val loginMotorista: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val tipo: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val descricao: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val registradoEm: Long? = null
)