package com.example.sistemaobras.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class AbrirDiarioRequest(
    @field:Schema(description = "Login do motorista (com token de motorista vale o do token).", example = "ana.souza")
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val loginMotorista: String,
    @field:Schema(description = "Veículo usado.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val veiculoId: String,
    val motivoUsoId: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val destino: String? = null,
    @field:Schema(description = "Hodômetro/horímetro na abertura.", example = "12500.5")
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorInicial: Double,
    val observacao: String? = null,
    // Parte 6B (offline, opcionais): ocorridoEm = hora do evento (ISO-8601 com fuso);
    // idLocal = UUID gerado no celular (reenvio não duplica); enviadoEm = hora do celular no envio.
    @field:Schema(description = "Hora do evento no celular, ISO-8601 com fuso (opcional).", example = "2026-10-19T12:00:00Z")
    val ocorridoEm: String? = null,
    @field:Schema(description = "UUID gerado no celular; reenvio com o mesmo valor não duplica (opcional).", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val idLocal: String? = null,
    @field:Schema(description = "Hora do celular no momento do envio, para medir o desvio do relógio (opcional).", example = "2026-10-19T12:05:00Z")
    val enviadoEm: String? = null
)