package com.example.sistemaobras.dto

data class MecanicoRequest(
    val nome: String,
    val telefone: String? = null,
    val especialidade: String? = null
)