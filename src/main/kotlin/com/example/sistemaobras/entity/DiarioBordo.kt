package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "diarios_bordo")
data class DiarioBordo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "turno_id", nullable = false)
    val turnoId: UUID? = null,

    @Column(name = "usuario_id", nullable = false)
    val usuarioId: UUID? = null,

    @Column(name = "veiculo_id", nullable = false)
    val veiculoId: UUID? = null,

    @Column(name = "motivo_uso_id")
    val motivoUsoId: UUID? = null,

    @Column(name = "destino")
    val destino: String? = null,

    @Column(name = "medidor_inicial", nullable = false)
    val medidorInicial: BigDecimal = BigDecimal.ZERO,

    @Column(name = "medidor_final")
    val medidorFinal: BigDecimal? = null,

    @Column(name = "medidor_percorrido")
    val medidorPercorrido: BigDecimal? = null,

    @Column(name = "observacao_fechamento")
    val observacaoFechamento: String? = null,

    @Column(name = "status", nullable = false, columnDefinition = "status_diario")
    val status: String = "aberto",

    @Column(name = "aberto_em", nullable = false)
    val abertoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "fechado_em")
    val fechadoEm: LocalDateTime? = null
)