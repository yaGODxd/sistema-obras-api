package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "mecanicos")
data class Mecanico(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "nome", nullable = false)
    val nome: String = "",

    @Column(name = "telefone")
    val telefone: String? = null,

    @Column(name = "especialidade")
    val especialidade: String? = null,

    @Column(name = "ativo", nullable = false)
    val ativo: Boolean = true,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now()
)