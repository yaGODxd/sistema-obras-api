package com.example.sistemaobras.dto

import java.util.UUID

data class VeiculoResponse(
    val id: UUID?,
    val tipoNome: String,
    val tipoMedidor: String,
    val placa: String?,
    val descricao: String,
    val status: String,
    val medidorAtual: Double,
    val marca: String? = null,
    val modelo: String? = null,
    val ano: Int? = null,
    val cor: String? = null,
    val renavam: String? = null,
    val chassi: String? = null,
    val comboio: Boolean = false
)