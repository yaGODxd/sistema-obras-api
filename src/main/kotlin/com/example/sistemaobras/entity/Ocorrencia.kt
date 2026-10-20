package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

object StatusOcorrencia {
    const val PENDENTE = "pendente"
    const val EM_ANDAMENTO = "em_andamento"
    const val RESOLVIDA = "resolvida"
    val TODOS = setOf(PENDENTE, EM_ANDAMENTO, RESOLVIDA)
}

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
    val registradoEm: LocalDateTime = LocalDateTime.now(java.time.ZoneOffset.UTC),

    // Coluna varchar com o vocabulário do enum status_ocorrencia do banco:
    // pendente (nasce assim, igual ao default do banco), em_andamento, resolvida.
    @Column(name = "status", nullable = false)
    val status: String = StatusOcorrencia.PENDENTE,

    @Column(name = "resolvida_em")
    val resolvidaEm: LocalDateTime? = null,

    // Parte 6B (colunas do SQL 1): hora do servidor, reenvio e horário suspeito.
    // registrado_em passa a ser a hora do evento (celular).
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