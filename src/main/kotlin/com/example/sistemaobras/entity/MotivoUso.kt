package com.example.sistemaobras.entity

import jakarta.persistence.*
import java.util.UUID

// Motivos de uso do veículo (tabela motivos_uso). Só leitura pela API (Parte 9, extra).
@Entity
@Table(name = "motivos_uso")
data class MotivoUso(
    @Id
    val id: UUID? = null,

    @Column(name = "descricao", nullable = false)
    val descricao: String = "",

    @Column(name = "ativo", nullable = false)
    val ativo: Boolean = true
)
