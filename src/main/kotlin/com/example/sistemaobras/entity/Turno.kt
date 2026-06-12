package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "turnos")
data class Turno(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "usuario_id", nullable = false)
    val usuarioId: UUID? = null,

    @Column(name = "status", nullable = false, columnDefinition = "status_turno")
    val status: String = "aberto",

    @Column(name = "aberto_em", nullable = false)
    val abertoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "fechado_em")
    val fechadoEm: LocalDateTime? = null
)