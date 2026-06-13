package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AbastecimentoDetalhadoResponse
import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.AbastecimentoResponse
import com.example.sistemaobras.repository.AbastecimentoRepository
import com.example.sistemaobras.repository.DiarioBordoRepository
import org.springframework.stereotype.Service
import java.util.UUID

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

    fun listarPorVeiculo(veiculoId: String): List<AbastecimentoDetalhadoResponse> {
        return abastecimentoRepository.findByVeiculo(veiculoId).map { row ->
            AbastecimentoDetalhadoResponse(
                id = UUID.fromString(row[0].toString()),
                tipoCombustivel = row[2].toString(),
                litros = row[3].toString().toDouble(),
                valorTotal = row[4].toString().toDouble(),
                posto = row[5]?.toString(),
                registradoEm = if (row[7] != null)
                    java.time.LocalDateTime.parse(row[7].toString().replace(" ", "T"))
                else null,
                veiculoDescricao = row[8].toString(),
                veiculoPlaca = row[9]?.toString(),
                motoristaNome = row[10].toString()
            )
        }
    }

    fun listarPorMotorista(login: String): List<AbastecimentoDetalhadoResponse> {
        return abastecimentoRepository.findByMotorista(login).map { row ->
            AbastecimentoDetalhadoResponse(
                id = UUID.fromString(row[0].toString()),
                tipoCombustivel = row[2].toString(),
                litros = row[3].toString().toDouble(),
                valorTotal = row[4].toString().toDouble(),
                posto = row[5]?.toString(),
                registradoEm = if (row[7] != null)
                    java.time.LocalDateTime.parse(row[7].toString().replace(" ", "T"))
                else null,
                veiculoDescricao = row[8].toString(),
                veiculoPlaca = row[9]?.toString(),
                motoristaNome = row[10].toString()
            )
        }
    }

    fun listarTodos(): List<AbastecimentoDetalhadoResponse> {
        return abastecimentoRepository.findTodos().map { row ->
            AbastecimentoDetalhadoResponse(
                id = UUID.fromString(row[0].toString()),
                tipoCombustivel = row[2].toString(),
                litros = row[3].toString().toDouble(),
                valorTotal = row[4].toString().toDouble(),
                posto = row[5]?.toString(),
                registradoEm = if (row[7] != null)
                    java.time.LocalDateTime.parse(row[7].toString().replace(" ", "T"))
                else null,
                veiculoDescricao = row[8].toString(),
                veiculoPlaca = row[9]?.toString(),
                motoristaNome = row[10].toString()
            )
        }
    }
}