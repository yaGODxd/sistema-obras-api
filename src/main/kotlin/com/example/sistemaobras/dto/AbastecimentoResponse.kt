package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class AbastecimentoResponse(
    val id: UUID?,
    val tipoCombustivel: String,
    val litros: Double,
    val valorTotal: Double,
    val posto: String?,
    val registradoEm: LocalDateTime?,
    // true quando o abastecimento foi feito pelo comboio em outro veículo.
    val comboio: Boolean = false,
    val veiculoAbastecidoId: UUID? = null,
    // Parte 6B: hora em que o servidor recebeu e marcação de horário suspeito.
    val recebidoEm: LocalDateTime? = null,
    val idLocal: UUID? = null,
    val horarioSuspeito: Boolean = false,
    val motivoSuspeita: String? = null
)