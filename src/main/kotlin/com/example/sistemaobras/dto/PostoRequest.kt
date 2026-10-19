package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank

data class PostoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val nome: String,
    val endereco: String? = null,
    val cidade: String? = null
)