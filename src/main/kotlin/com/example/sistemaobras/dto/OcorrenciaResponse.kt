package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class OcorrenciaResponse(
    val id: UUID?,
    val diarioId: UUID?,
    val tipo: String,
    val descricao: String,
    val latitude: Double?,
    val longitude: Double?,
    val registradoEm: LocalDateTime
)