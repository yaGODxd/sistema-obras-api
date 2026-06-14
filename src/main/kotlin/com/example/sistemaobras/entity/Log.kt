package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "logs")
data class Log(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "usuario_login", nullable = false)
    val usuarioLogin: String = "",

    @Column(name = "usuario_nome")
    val usuarioNome: String? = null,

    @Column(name = "acao", nullable = false)
    val acao: String = "",

    @Column(name = "descricao", nullable = false)
    val descricao: String = "",

    @Column(name = "ip")
    val ip: String? = null,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now()
)