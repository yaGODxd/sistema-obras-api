package com.example.sistemaobras.dto

import io.swagger.v3.oas.annotations.media.Schema

data class TurnoRequest(
    @field:Schema(description = "Login do motorista (com token de motorista vale o do token).", example = "ana.souza")
    val login: String,
    // Parte 6B (offline, opcionais): ocorridoEm = hora do evento (ISO-8601 com fuso);
    // idLocal = UUID gerado no celular (reenvio não duplica); enviadoEm = hora do celular no envio.
    @field:Schema(description = "Hora do evento no celular, ISO-8601 com fuso (opcional).", example = "2026-10-19T12:00:00Z")
    val ocorridoEm: String? = null,
    @field:Schema(description = "UUID gerado no celular; reenvio com o mesmo valor não duplica (opcional).", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val idLocal: String? = null,
    @field:Schema(description = "Hora do celular no momento do envio, para medir o desvio do relógio (opcional).", example = "2026-10-19T12:05:00Z")
    val enviadoEm: String? = null
)