package com.example.sistemaobras.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class FecharDiarioRequest(
    @field:Schema(description = "Hodômetro/horímetro no fechamento (não pode ser menor que o inicial).", example = "12620.0")
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorFinal: Double,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val observacaoFechamento: String? = null,
    // Parte 6B (offline, opcionais): ocorridoEm = hora do evento (ISO-8601 com fuso);
    // idLocal = UUID gerado no celular (reenvio não duplica); enviadoEm = hora do celular no envio.
    @field:Schema(description = "Hora do evento no celular, ISO-8601 com fuso (opcional).", example = "2026-10-19T12:00:00Z")
    val ocorridoEm: String? = null,
    @field:Schema(description = "UUID gerado no celular; reenvio com o mesmo valor não duplica (opcional).", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val idLocal: String? = null,
    @field:Schema(description = "Hora do celular no momento do envio, para medir o desvio do relógio (opcional).", example = "2026-10-19T12:05:00Z")
    val enviadoEm: String? = null
)