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

    @Column(name = "veiculo_abastecido_id")
    val veiculoAbastecidoId: UUID? = null,

    @Column(name = "sincronizado", nullable = false)
    val sincronizado: Boolean = false,

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
    val motivoSuspeita: String? = null,

    // Usuário que registrou (no comboio não é o dono do diário). Antigos ficam nulos.
    @Column(name = "registrado_por")
    val registradoPor: UUID? = null
)