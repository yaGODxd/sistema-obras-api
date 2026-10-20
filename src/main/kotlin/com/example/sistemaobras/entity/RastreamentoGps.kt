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

    // Hora do evento (celular, UTC) a partir da Parte 6B.
    @Column(name = "registrado_em", nullable = false)
    val registradoEm: LocalDateTime = LocalDateTime.now(java.time.ZoneOffset.UTC),

    // Parte 6B (colunas do SQL 1): hora do servidor, reenvio e horário suspeito.
    @Column(name = "recebido_em")
    val recebidoEm: LocalDateTime? = null,

    @Column(name = "id_local")
    val idLocal: UUID? = null,

    @Column(name = "desvio_relogio_seg")
    val desvioRelogioSeg: Int? = null,

    @Column(name = "horario_suspeito", nullable = false)
    val horarioSuspeito: Boolean = false,

    @Column(name = "motivo_suspeita")
    val motivoSuspeita: String? = null
)