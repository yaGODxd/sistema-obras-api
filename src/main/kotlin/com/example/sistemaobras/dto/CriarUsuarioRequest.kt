package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CriarUsuarioRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val nomeCompleto: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val cpf: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 255, message = MensagensValidacao.TAMANHO)
    val login: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    @field:Size(max = 72, message = MensagensValidacao.TAMANHO)
    val senha: String,
    @field:Pattern(regexp = "gestor|motorista", message = MensagensValidacao.PERFIL)
    val perfil: String = "motorista",
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