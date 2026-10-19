package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank

data class OcorrenciaRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val loginMotorista: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val tipo: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val descricao: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val registradoEm: Long? = null
)