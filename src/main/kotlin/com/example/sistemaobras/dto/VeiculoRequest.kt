package com.example.sistemaobras.dto

data class VeiculoRequest(
    val tipoId: String,
    val placa: String?,
    val descricao: String,
    val medidorAtual: Double = 0.0
)