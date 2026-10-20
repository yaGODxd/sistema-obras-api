package com.example.sistemaobras.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

// POST /rastreamento/lote (Parte 6B): pontos guardados no celular durante o uso offline.
// No máximo 200 pontos por chamada (acima disso: 400 LOTE_GRANDE_DEMAIS).
data class RastreamentoLoteRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val diarioId: String,
    val enviadoEm: String? = null,
    @field:NotNull(message = MensagensValidacao.OBRIGATORIO)
    @field:Valid
    val pontos: List<PontoLoteRequest>
)

data class PontoLoteRequest(
    val latitude: Double,
    val longitude: Double,
    val velocidade: Double? = null,
    val ocorridoEm: String? = null,
    val idLocal: String? = null
)

// Resultado de cada ponto, na mesma ordem do pedido:
// gravado | duplicado (idLocal já recebido) | descartado_proximo | recusado (com codigo e erro).
data class ResultadoPontoResponse(
    val indice: Int,
    val idLocal: String?,
    val resultado: String,
    val codigo: String? = null,
    val erro: String? = null
)

data class RastreamentoLoteResponse(
    val diarioId: String,
    val recebidos: Int,
    val gravados: Int,
    val duplicados: Int,
    val descartados: Int,
    val recusados: Int,
    val pontos: List<ResultadoPontoResponse>
)
