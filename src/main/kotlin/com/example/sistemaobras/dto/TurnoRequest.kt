package com.example.sistemaobras.dto

data class TurnoRequest(
    val login: String,
    // Parte 6B (offline, opcionais): ocorridoEm = hora do evento (ISO-8601 com fuso);
    // idLocal = UUID gerado no celular (reenvio não duplica); enviadoEm = hora do celular no envio.
    val ocorridoEm: String? = null,
    val idLocal: String? = null,
    val enviadoEm: String? = null
)