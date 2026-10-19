package com.example.sistemaobras.dto

import java.time.LocalDateTime
import java.util.UUID

data class DefinirPinRequest(
    val senha: String,
    val novoPin: String
)

data class AssinarDiarioRequest(
    val pin: String
)

data class EditarDiarioRequest(
    val destino: String? = null,
    val motivoUsoId: String? = null,
    val observacaoFechamento: String? = null
)

data class PinStatusResponse(
    val definido: Boolean
)

data class DiarioDetalheResponse(
    val id: UUID?,
    val veiculoDescricao: String,
    val veiculoPlaca: String?,
    val motoristaLogin: String?,
    val motoristaNome: String,
    val medidorInicial: Double,
    val medidorFinal: Double?,
    val medidorPercorrido: Double?,
    val destino: String?,
    val motivoUsoId: String?,
    val observacaoFechamento: String?,
    val status: String,
    val abertoEm: LocalDateTime?,
    val fechadoEm: LocalDateTime?,
    val assinadoEm: LocalDateTime?,
    // Parte 4: preenchidos no GET /diarios/{id}; nas rotas de assinatura só veiculoId.
    val veiculoId: UUID? = null,
    val tipoMedidor: String? = null,
    val motivoUso: String? = null
)

data class VerificacaoDiarioResponse(
    val assinado: Boolean,
    val integro: Boolean,
    val assinadoEm: LocalDateTime?,
    val mensagem: String
)