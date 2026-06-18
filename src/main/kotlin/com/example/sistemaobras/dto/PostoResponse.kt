package com.example.sistemaobras.dto

import java.util.UUID

data class PostoResponse(
    val id: UUID?,
    val nome: String,
    val endereco: String? = null,
    val cidade: String? = null,
    val ativo: Boolean = true
)