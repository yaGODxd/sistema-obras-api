package com.example.sistemaobras.dto

data class AtualizarUsuarioRequest(
    val nomeCompleto: String? = null,
    val fotoPerfil: String? = null,
    val matricula: String? = null,
    val vinculo: String? = null,
    val secretariaId: Int? = null,
    val categoriaCnh: String? = null,
    val validadeCnh: String? = null,
    val telefone: String? = null
)