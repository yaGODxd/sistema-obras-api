package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class AbastecimentoDetalhadoResponse(
    val id: UUID?,
    val tipoCombustivel: String,
    val litros: Double,
    val valorTotal: Double,
    val posto: String?,
    val registradoEm: LocalDateTime?,
    val veiculoDescricao: String,
    val veiculoPlaca: String?,
    val motoristaNome: String
)