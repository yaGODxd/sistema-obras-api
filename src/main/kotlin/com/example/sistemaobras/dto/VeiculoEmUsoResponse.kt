package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class VeiculoEmUsoResponse(
    val id: UUID?,
    val tipoNome: String,
    val placa: String?,
    val descricao: String,
    val medidorAtual: Double,
    val motoristaId: UUID?,
    val motoristaNome: String,
    val motoristaLogin: String,
    val diarioAbertoEm: LocalDateTime?
)