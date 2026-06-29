package com.example.sistemaobras.dto

data class OcorrenciaRequest(
    val loginMotorista: String,
    val tipo: String,
    val descricao: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val registradoEm: Long? = null
)