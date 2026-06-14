package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class LogResponse(
    val id: UUID?,
    val usuarioLogin: String,
    val usuarioNome: String?,
    val acao: String,
    val descricao: String,
    val ip: String?,
    val criadoEm: LocalDateTime
)