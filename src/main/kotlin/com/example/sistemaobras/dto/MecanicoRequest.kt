package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class MecanicoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val nome: String,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val telefone: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val especialidade: String? = null
)