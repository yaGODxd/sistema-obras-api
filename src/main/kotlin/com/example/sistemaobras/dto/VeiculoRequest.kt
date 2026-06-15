package com.example.sistemaobras.dto

data class VeiculoRequest(
    val tipoId: String,
    val placa: String?,
    val descricao: String,
    val medidorAtual: Double = 0.0,
    val marca: String? = null,
    val modelo: String? = null,
    val ano: Int? = null,
    val cor: String? = null,
    val renavam: String? = null,
    val chassi: String? = null
)