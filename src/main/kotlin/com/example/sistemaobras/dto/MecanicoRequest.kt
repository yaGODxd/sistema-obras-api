package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank

data class MecanicoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val nome: String,
    val telefone: String? = null,
    val especialidade: String? = null
)