package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "abastecimentos")
data class Abastecimento(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "diario_id", nullable = false)
    val diarioId: UUID? = null,

    @Column(name = "tipo_combustivel", nullable = false)
    val tipoCombustivel: String = "",

    @Column(name = "litros", nullable = false)
    val litros: BigDecimal = BigDecimal.ZERO,

    @Column(name = "valor_total", nullable = false)
    val valorTotal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "posto")
    val posto: String? = null,

    @Column(name = "sincronizado", nullable = false)
    val sincronizado: Boolean = false,

    @Column(name = "registrado_em", nullable = false)
    val registradoEm: LocalDateTime = LocalDateTime.now()
)