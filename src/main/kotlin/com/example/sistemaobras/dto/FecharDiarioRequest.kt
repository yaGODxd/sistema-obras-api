package com.example.sistemaobras.dto

data class FecharDiarioRequest(
    val medidorFinal: Double,
    val observacaoFechamento: String? = null,
    val fechadoEm: Long? = null
)