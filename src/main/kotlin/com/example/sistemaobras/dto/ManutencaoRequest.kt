package com.example.sistemaobras.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class ManutencaoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val veiculoId: String,
    val ocorrenciaId: String? = null,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val tipo: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val descricao: String,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val mecanicoResponsavel: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val oficina: String? = null,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val custoMaoObra: Double = 0.0,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val observacoes: String? = null,
    val fotoNotaFiscal: String? = null,
    @field:Valid
    val pecas: List<PecaRequest> = emptyList()
)