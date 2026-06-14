package com.example.sistemaobras.dto

data class ManutencaoRequest(
    val veiculoId: String,
    val ocorrenciaId: String? = null,
    val tipo: String,
    val descricao: String,
    val mecanicoResponsavel: String? = null,
    val oficina: String? = null,
    val custoMaoObra: Double = 0.0,
    val observacoes: String? = null,
    val pecas: List<PecaRequest> = emptyList()
)