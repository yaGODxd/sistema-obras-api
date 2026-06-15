package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import jakarta.persistence.Convert

@Entity
@Table(name = "usuarios")
data class Usuario(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "nome_completo", nullable = false)
    val nomeCompleto: String = "",

    @Column(name = "cpf", nullable = false, unique = true)
    val cpf: String = "",

    @Column(name = "login", nullable = false, unique = true)
    val login: String = "",

    @Column(name = "senha_hash", nullable = false)
    val senhaHash: String = "",

    @Column(name = "perfil", nullable = false, columnDefinition = "perfil_usuario")
    @Convert(converter = PerfilUsuarioConverter::class)
    val perfil: PerfilUsuario = PerfilUsuario.motorista,

    @Column(name = "foto_perfil")
    val fotoPerfil: String? = null,

    @Column(name = "ativo", nullable = false)
    val ativo: Boolean = true,

    @Column(name = "token_fcm")
    val tokenFcm: String? = null,

    @Column(name = "matricula")
    val matricula: String? = null,

    @Column(name = "vinculo")
    val vinculo: String? = null,

    @Column(name = "secretaria_id")
    val secretariaId: Int? = null,

    @Column(name = "categoria_cnh")
    val categoriaCnh: String? = null,

    @Column(name = "validade_cnh")
    val validadeCnh: LocalDate? = null,

    @Column(name = "telefone")
    val telefone: String? = null,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    val atualizadoEm: LocalDateTime = LocalDateTime.now()
)

enum class PerfilUsuario {
    gestor, motorista
}