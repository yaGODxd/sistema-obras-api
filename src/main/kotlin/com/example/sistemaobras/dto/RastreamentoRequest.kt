package com.example.sistemaobras.dto

data class RastreamentoRequest(
    val loginMotorista: String,
    val latitude: Double,
    val longitude: Double,
    val velocidade: Double? = null,
    // Parte 6B (offline, opcionais): ocorridoEm = hora do evento (ISO-8601 com fuso);
    // idLocal = UUID gerado no celular (reenvio não duplica); enviadoEm = hora do celular no envio.
    val diarioId: String? = null,
    val ocorridoEm: String? = null,
    val idLocal: String? = null,
    val enviadoEm: String? = null
)