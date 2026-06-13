package com.example.sistemaobras.dto

data class AbastecimentoRequest(
    val loginMotorista: String,
    val tipoCombustivel: String,
    val litros: Double,
    val valorTotal: Double,
    val posto: String? = null
)