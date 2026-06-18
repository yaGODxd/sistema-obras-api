package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class ManutencaoResponse(
    val id: UUID?,
    val veiculoId: UUID?,
    val veiculoDescricao: String,
    val veiculoPlaca: String?,
    val ocorrenciaId: UUID?,
    val tipo: String,
    val descricao: String,
    val mecanicoResponsavel: String?,
    val oficina: String?,
    val custoMaoObra: Double,
    val custoPecas: Double,
    val custoTotal: Double,
    val status: String,
    val abertaEm: LocalDateTime,
    val concluidaEm: LocalDateTime?,
    val observacoes: String?,
    val fotoNotaFiscal: String? = null,
    val pecas: List<PecaResponse>
)