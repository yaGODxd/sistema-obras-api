package com.example.sistemaobras.dto

import java.util.UUID

data class PecaResponse(
    val id: UUID?,
    val nomePeca: String,
    val codigoPeca: String?,
    val marcaPeca: String?,
    val quantidade: Int,
    val valorUnitario: Double,
    val valorTotal: Double
)