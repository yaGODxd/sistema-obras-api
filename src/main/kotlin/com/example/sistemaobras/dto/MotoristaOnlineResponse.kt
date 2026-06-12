package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class MotoristaOnlineResponse(
    val id: UUID?,
    val nomeCompleto: String,
    val login: String,
    val fotoPerfil: String?,
    val turnoAbertoEm: LocalDateTime?
)