package com.example.sistemaobras.dto

import io.swagger.v3.oas.annotations.media.Schema

data class LoginRequest(
    @field:Schema(description = "Login do usuário.", example = "ana.souza")
    val login: String,
    @field:Schema(description = "Senha.", example = "minhaSenha")
    val senha: String
)