package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

// PUT /ocorrencias/{id}/status: o gestor muda o status à mão.
data class AtualizarStatusOcorrenciaRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Pattern(regexp = "pendente|em_andamento|resolvida", message = MensagensValidacao.STATUS_OCORRENCIA)
    val status: String
)
