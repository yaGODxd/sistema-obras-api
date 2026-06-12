package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class TurnoResponse(
    val id: UUID?,
    val login: String,
    val status: String,
    val abertoEm: LocalDateTime?
)