package com.example.sistemaobras.dto

import jakarta.validation.constraints.Size

data class AtualizarUsuarioRequest(
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val nomeCompleto: String? = null,
    val fotoPerfil: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val matricula: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val vinculo: String? = null,
    val secretariaId: Int? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val categoriaCnh: String? = null,
    val validadeCnh: String? = null,
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val telefone: String? = null
)