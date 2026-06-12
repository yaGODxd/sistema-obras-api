package com.example.sistemaobras.dto

data class LoginResponse(
    val token: String,
    val nome: String,
    val perfil: String
)