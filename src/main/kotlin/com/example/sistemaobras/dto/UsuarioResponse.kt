package com.example.sistemaobras.dto

import java.time.LocalDate
import java.util.UUID

data class UsuarioResponse(
    val id: UUID?,
    val nomeCompleto: String,
    val login: String,
    val perfil: String,
    val fotoPerfil: String? = null,
    val ativo: Boolean = true,
    val matricula: String? = null,
    val vinculo: String? = null,
    val secretariaId: Int? = null,
    val secretariaNome: String? = null,
    val categoriaCnh: String? = null,
    val validadeCnh: LocalDate? = null,
    val telefone: String? = null
)