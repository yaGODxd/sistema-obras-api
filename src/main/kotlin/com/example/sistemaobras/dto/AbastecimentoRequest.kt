package com.example.sistemaobras.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class AbastecimentoRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val loginMotorista: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val tipoCombustivel: String,
    @field:Schema(description = "Litros abastecidos (maior que zero).", example = "45.5")
    @field:Positive(message = MensagensValidacao.MAIOR_QUE_ZERO)
    val litros: Double,
    @field:Schema(description = "Valor pago (pode ser 0 no comboio).", example = "280.90")
    @field:PositiveOrZero(message = MensagensValidacao.NAO_NEGATIVO)
    val valorTotal: Double,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val posto: String? = null,
    @field:Schema(description = "Só no comboio: veículo que recebeu o combustível.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val veiculoAbastecidoId: String? = null,
    @field:Schema(description = "Hora do evento em milissegundos (formato antigo; prefira ocorridoEm).", example = "1792411200000")
    val registradoEm: Long? = null,
    // Parte 6B (offline, opcionais): ocorridoEm = hora do evento (ISO-8601 com fuso);
    // idLocal = UUID gerado no celular (reenvio não duplica); enviadoEm = hora do celular no envio.
    @field:Schema(description = "Diário do evento (id devolvido ao abrir). Sem ele: diário aberto agora (opcional).", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val diarioId: String? = null,
    @field:Schema(description = "Hora do evento no celular, ISO-8601 com fuso (opcional).", example = "2026-10-19T12:00:00Z")
    val ocorridoEm: String? = null,
    @field:Schema(description = "UUID gerado no celular; reenvio com o mesmo valor não duplica (opcional).", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val idLocal: String? = null,
    @field:Schema(description = "Hora do celular no momento do envio, para medir o desvio do relógio (opcional).", example = "2026-10-19T12:05:00Z")
    val enviadoEm: String? = null
)