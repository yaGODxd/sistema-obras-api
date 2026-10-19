package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

// PUT /ocorrencias/{id}/status: o gestor muda o status à mão.
data class AtualizarStatusOcorrenciaRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Pattern(regexp = "aberta|em_andamento|concluida|descartada", message = MensagensValidacao.STATUS_OCORRENCIA)
    val status: String
)
