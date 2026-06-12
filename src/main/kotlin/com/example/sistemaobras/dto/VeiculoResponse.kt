package com.example.sistemaobras.dto

import java.util.UUID

data class VeiculoResponse(
    val id: UUID?,
    val tipoNome: String,
    val tipoMedidor: String,
    val placa: String?,
    val descricao: String,
    val status: String,
    val medidorAtual: Double
)