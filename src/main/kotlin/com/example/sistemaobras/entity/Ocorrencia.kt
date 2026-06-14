package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "ocorrencias")
data class Ocorrencia(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "diario_id", nullable = false)
    val diarioId: UUID? = null,

    @Column(name = "tipo", nullable = false)
    val tipo: String = "",

    @Column(name = "descricao", nullable = false)
    val descricao: String = "",

    @Column(name = "latitude")
    val latitude: BigDecimal? = null,

    @Column(name = "longitude")
    val longitude: BigDecimal? = null,

    @Column(name = "registrado_em", nullable = false)
    val registradoEm: LocalDateTime = LocalDateTime.now()
)