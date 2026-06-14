    package com.example.sistemaobras.dto

    data class DiarioResumoMensalResponse(
        val veiculoId: String,
        val veiculoDescricao: String,
        val veiculoPlaca: String?,
        val mes: Int,
        val ano: Int,
        val totalDiarios: Int,
        val primeiroMedidor: Double,
        val ultimoMedidor: Double,
        val totalPercorrido: Double
    )