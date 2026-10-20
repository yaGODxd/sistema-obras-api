package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class AbrirDiarioRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val loginMotorista: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val veiculoId: String,
    val motivoUsoId: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val destino: String? = null,
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val medidorInicial: Double,
    val observacao: String? = null,
    // Parte 6B (offline, opcionais): ocorridoEm = hora do evento (ISO-8601 com fuso);
    // idLocal = UUID gerado no celular (reenvio não duplica); enviadoEm = hora do celular no envio.
    val ocorridoEm: String? = null,
    val idLocal: String? = null,
    val enviadoEm: String? = null
)