package com.example.sistemaobras.dto

data class AbrirDiarioRequest(
    val loginMotorista: String,
    val veiculoId: String,
    val medidorInicial: Double,
    val destino: String? = null,
    val motivoUsoId: String? = null,
    val observacao: String? = null,
    val abertoEm: Long? = null
)