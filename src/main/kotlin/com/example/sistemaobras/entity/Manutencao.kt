package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "manutencoes")
data class Manutencao(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "veiculo_id", nullable = false)
    val veiculoId: UUID? = null,

    @Column(name = "ocorrencia_id")
    val ocorrenciaId: UUID? = null,

    @Column(name = "tipo", nullable = false)
    val tipo: String = "",

    @Column(name = "descricao", nullable = false)
    val descricao: String = "",

    @Column(name = "mecanico_responsavel")
    val mecanicoResponsavel: String? = null,

    @Column(name = "oficina")
    val oficina: String? = null,

    @Column(name = "custo_mao_obra", nullable = false)
    val custoMaoObra: BigDecimal = BigDecimal.ZERO,

    @Column(name = "status", nullable = false)
    val status: String = "aberta",

    @Column(name = "aberta_em", nullable = false)
    val abertaEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "concluida_em")
    val concluidaEm: LocalDateTime? = null,

    @Column(name = "observacoes")
    val observacoes: String? = null,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    val atualizadoEm: LocalDateTime = LocalDateTime.now()
)