package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class RastreamentoResponse(
    val id: UUID?,
    val diarioId: UUID?,
    val latitude: Double,
    val longitude: Double,
    val velocidade: Double?,
    val registradoEm: LocalDateTime?
)

data class UltimaPosicaoResponse(
    val login: String,
    val nomeCompleto: String,
    val latitude: Double,
    val longitude: Double,
    val registradoEm: LocalDateTime?,
    val veiculoDescricao: String?
)