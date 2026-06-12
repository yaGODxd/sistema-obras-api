package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "rastreamento_gps")
data class RastreamentoGps(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "diario_id", nullable = false)
    val diarioId: UUID? = null,

    @Column(name = "latitude", nullable = false)
    val latitude: BigDecimal = BigDecimal.ZERO,

    @Column(name = "longitude", nullable = false)
    val longitude: BigDecimal = BigDecimal.ZERO,

    @Column(name = "velocidade")
    val velocidade: BigDecimal? = null,

    @Column(name = "registrado_em", nullable = false)
    val registradoEm: LocalDateTime = LocalDateTime.now()
)