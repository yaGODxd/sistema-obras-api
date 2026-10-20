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
    val registradoEm: LocalDateTime,
    val status: String = "pendente",
    val resolvidaEm: LocalDateTime? = null,
    // Parte 6B: hora em que o servidor recebeu e marcação de horário suspeito.
    val recebidoEm: LocalDateTime? = null,
    val idLocal: UUID? = null,
    val horarioSuspeito: Boolean = false,
    val motivoSuspeita: String? = null
)