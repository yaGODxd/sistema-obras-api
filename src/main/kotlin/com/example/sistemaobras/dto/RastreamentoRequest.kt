package com.example.sistemaobras.dto

data class RastreamentoRequest(
    val loginMotorista: String,
    val latitude: Double,
    val longitude: Double,
    val velocidade: Double? = null
)