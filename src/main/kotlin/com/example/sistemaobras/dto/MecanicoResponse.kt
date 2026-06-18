package com.example.sistemaobras.dto

import java.util.UUID

data class MecanicoResponse(
    val id: UUID?,
    val nome: String,
    val telefone: String? = null,
    val especialidade: String? = null,
    val ativo: Boolean = true
)