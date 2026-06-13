package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.AbastecimentoResponse
import com.example.sistemaobras.repository.AbastecimentoRepository
import com.example.sistemaobras.repository.DiarioBordoRepository
import org.springframework.stereotype.Service

@Service
class AbastecimentoService(
    private val abastecimentoRepository: AbastecimentoRepository,
    private val diarioRepository: DiarioBordoRepository
) {

    fun registrar(request: AbastecimentoRequest): AbastecimentoResponse {
        if (diarioRepository.temDiarioAberto(request.loginMotorista) == 0L)
            throw RuntimeException("Motorista não tem diário aberto")

        abastecimentoRepository.inserirAbastecimento(
            login = request.loginMotorista,
            tipoCombustivel = request.tipoCombustivel,
            litros = request.litros,
            valorTotal = request.valorTotal,
            posto = request.posto
        )

        return AbastecimentoResponse(
            id = null,
            tipoCombustivel = request.tipoCombustivel,
            litros = request.litros,
            valorTotal = request.valorTotal,
            posto = request.posto,
            registradoEm = java.time.LocalDateTime.now()
        )
    }

    fun listarPorDiario(diarioId: String): List<AbastecimentoResponse> {
        return abastecimentoRepository.findByDiarioId(diarioId).map { a ->
            AbastecimentoResponse(
                id = a.id,
                tipoCombustivel = a.tipoCombustivel,
                litros = a.litros.toDouble(),
                valorTotal = a.valorTotal.toDouble(),
                posto = a.posto,
                registradoEm = a.registradoEm
            )
        }
    }

    fun listarDiarioAberto(login: String): List<AbastecimentoResponse> {
        return abastecimentoRepository.findByDiarioAberto(login).map { a ->
            AbastecimentoResponse(
                id = a.id,
                tipoCombustivel = a.tipoCombustivel,
                litros = a.litros.toDouble(),
                valorTotal = a.valorTotal.toDouble(),
                posto = a.posto,
                registradoEm = a.registradoEm
            )
        }
    }
}