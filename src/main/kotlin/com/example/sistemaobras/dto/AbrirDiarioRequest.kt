package com.example.sistemaobras.dto

data class AbrirDiarioRequest(
    val loginMotorista: String,
    val veiculoId: String,
    val motivoUsoId: String? = null,
    val destino: String? = null,
    val medidorInicial: Double,
    val observacao: String? = null
)