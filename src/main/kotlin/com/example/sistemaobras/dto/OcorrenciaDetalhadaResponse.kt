package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class OcorrenciaDetalhadaResponse(
    val id: UUID?,
    val diarioId: UUID?,
    val tipo: String,
    val descricao: String,
    val latitude: Double?,
    val longitude: Double?,
    val registradoEm: LocalDateTime,
    val motoristaNome: String,
    val motoristaLogin: String,
    val motoristaFoto: String? = null,
    val veiculoId: UUID?,
    val veiculoDescricao: String,
    val veiculoPlaca: String?
)