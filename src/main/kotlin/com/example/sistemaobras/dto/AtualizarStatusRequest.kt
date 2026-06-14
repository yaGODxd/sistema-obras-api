package com.example.sistemaobras.dto

data class AtualizarStatusRequest(
    val status: String,
    val observacoes: String? = null
)