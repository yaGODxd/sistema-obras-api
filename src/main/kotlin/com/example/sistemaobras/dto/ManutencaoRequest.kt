package com.example.sistemaobras.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

data class ManutencaoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val veiculoId: String,
    val ocorrenciaId: String? = null,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val tipo: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val descricao: String,
    val mecanicoResponsavel: String? = null,
    val oficina: String? = null,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val custoMaoObra: Double = 0.0,
    val observacoes: String? = null,
    val fotoNotaFiscal: String? = null,
    @field:Valid
    val pecas: List<PecaRequest> = emptyList()
)