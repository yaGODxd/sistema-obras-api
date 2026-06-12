package com.example.sistemaobras.dto

data class CriarUsuarioRequest(
    val nomeCompleto: String,
    val cpf: String,
    val login: String,
    val senha: String,
    val perfil: String = "motorista"
)