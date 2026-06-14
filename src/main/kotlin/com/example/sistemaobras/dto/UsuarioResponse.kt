package com.example.sistemaobras.dto

import java.util.UUID

data class UsuarioResponse(
    val id: UUID?,
    val nomeCompleto: String,
    val login: String,
    val perfil: String,
    val fotoPerfil: String? = null,
    val ativo: Boolean = true
)