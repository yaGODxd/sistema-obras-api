package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class DiarioResponse(
    val id: UUID?,
    val veiculoDescricao: String,
    val veiculoPlaca: String?,
    val motoristaNome: String,
    val medidorInicial: Double,
    val medidorFinal: Double?,
    val medidorPercorrido: Double?,
    val destino: String?,
    val status: String,
    val abertoEm: LocalDateTime?,
    val fechadoEm: LocalDateTime?,
    val veiculoId: UUID? = null,
    // Tipo de medidor do veículo (vem de tipo_veiculo.medidor).
    val tipoMedidor: String? = null
)