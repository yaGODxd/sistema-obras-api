package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

// Parte 9: metadados de uma foto (NUNCA os bytes; a imagem sai só em GET /fotos/{id}).
data class FotoResponse(
    val id: UUID,
    val diarioId: UUID,
    val tipo: String,
    val ocorrenciaId: UUID?,
    val abastecimentoId: UUID?,
    val tiradaEm: LocalDateTime?,
    val recebidaEm: LocalDateTime?,
    val tamanhoBytes: Int?,
    val tipoMime: String?,
    val hashSha256: String?,
    val idLocal: UUID?,
    val horarioSuspeito: Boolean,
    val motivoSuspeita: String?
)

// GET /fotos/uso (gestor): espaço ocupado pelas fotos e pelo banco inteiro.
data class UsoFotosResponse(
    val quantidadeFotos: Long,
    val bytesFotos: Long,
    val megabytesFotos: Double,
    val bytesTabelaFotos: Long,
    val megabytesTabelaFotos: Double,
    val bytesBanco: Long,
    val megabytesBanco: Double
)
