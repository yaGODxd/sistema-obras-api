package com.example.sistemaobras.dto

data class CriarUsuarioRequest(
    val nomeCompleto: String,
    val cpf: String,
    val login: String,
    val senha: String,
    val perfil: String = "motorista",
    val matricula: String? = null,
    val vinculo: String? = null,
    val secretariaId: Int? = null,
    val categoriaCnh: String? = null,
    val validadeCnh: String? = null,
    val telefone: String? = null
)