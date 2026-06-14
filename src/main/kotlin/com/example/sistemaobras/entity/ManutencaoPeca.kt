package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "manutencao_pecas")
data class ManutencaoPeca(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "manutencao_id", nullable = false)
    val manutencaoId: UUID? = null,

    @Column(name = "nome_peca", nullable = false)
    val nomePeca: String = "",

    @Column(name = "codigo_peca")
    val codigoPeca: String? = null,

    @Column(name = "marca_peca")
    val marcaPeca: String? = null,

    @Column(name = "quantidade", nullable = false)
    val quantidade: Int = 1,

    @Column(name = "valor_unitario", nullable = false)
    val valorUnitario: BigDecimal = BigDecimal.ZERO,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now()
)