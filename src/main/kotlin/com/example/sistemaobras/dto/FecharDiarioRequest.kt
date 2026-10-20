package com.example.sistemaobras.dto

import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class FecharDiarioRequest(
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorFinal: Double,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val observacaoFechamento: String? = null,
    // Parte 6B (offline, opcionais): ocorridoEm = hora do evento (ISO-8601 com fuso);
    // idLocal = UUID gerado no celular (reenvio não duplica); enviadoEm = hora do celular no envio.
    val ocorridoEm: String? = null,
    val idLocal: String? = null,
    val enviadoEm: String? = null
)