package com.example.sistemaobras.dto

import jakarta.validation.constraints.Size

data class AtualizarPerfilRequest(
    val fotoPerfil: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val nomeCompleto: String? = null
)