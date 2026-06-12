package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "veiculos")
data class Veiculo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "tipo_id", nullable = false)
    val tipoId: UUID? = null,

    @Column(name = "placa")
    val placa: String? = null,

    @Column(name = "descricao", nullable = false)
    val descricao: String = "",

    @Column(name = "status", nullable = false, columnDefinition = "status_veiculo")
    val status: String = "disponivel",

    @Column(name = "medidor_atual", nullable = false)
    val medidorAtual: BigDecimal = BigDecimal.ZERO,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    val atualizadoEm: LocalDateTime = LocalDateTime.now()
)