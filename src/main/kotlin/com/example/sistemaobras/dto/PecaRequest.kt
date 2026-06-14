package com.example.sistemaobras.dto

data class PecaRequest(
    val nomePeca: String,
    val codigoPeca: String? = null,
    val marcaPeca: String? = null,
    val quantidade: Int,
    val valorUnitario: Double
)