package com.example.sistemaobras.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CriarUsuarioRequest(
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val nomeCompleto: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val cpf: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val login: String,
    @field:NotBlank(message = MensagensValidacao.OBRIGATORIO)
    val senha: String,
    @field:Pattern(regexp = "gestor|motorista", message = MensagensValidacao.PERFIL)
    val perfil: String = "motorista",
    val matricula: String? = null,
    val vinculo: String? = null,
    val secretariaId: Int? = null,
    val categoriaCnh: String? = null,
    val validadeCnh: String? = null,
    val telefone: String? = null
)