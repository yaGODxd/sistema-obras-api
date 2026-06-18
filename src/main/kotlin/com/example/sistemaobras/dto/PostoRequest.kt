package com.example.sistemaobras.dto

data class PostoRequest(
    val nome: String,
    val endereco: String? = null,
    val cidade: String? = null
)