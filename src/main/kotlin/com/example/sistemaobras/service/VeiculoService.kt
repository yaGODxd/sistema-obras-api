package com.example.sistemaobras.service

import com.example.sistemaobras.dto.VeiculoEmUsoResponse
import com.example.sistemaobras.dto.VeiculoRequest
import com.example.sistemaobras.dto.VeiculoResponse
import com.example.sistemaobras.repository.VeiculoRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class VeiculoService(
    private val veiculoRepository: VeiculoRepository
) {

    fun listarTodos(): List<VeiculoResponse> {
        return veiculoRepository.findAllAtivos().map { v ->
            VeiculoResponse(
                id = v.id,
                tipoNome = "",
                tipoMedidor = "",
                placa = v.placa,
                descricao = v.descricao,
                status = v.status,
                medidorAtual = v.medidorAtual.toDouble()
            )
        }
    }

    fun listarDisponiveis(): List<VeiculoResponse> {
        return veiculoRepository.findDisponiveis().map { v ->
            VeiculoResponse(
                id = v.id,
                tipoNome = "",
                tipoMedidor = "",
                placa = v.placa,
                descricao = v.descricao,
                status = v.status,
                medidorAtual = v.medidorAtual.toDouble()
            )
        }
    }

    fun listarEmUso(): List<VeiculoEmUsoResponse> {
        return veiculoRepository.findEmUso().map { row ->
            VeiculoEmUsoResponse(
                id = UUID.fromString(row[0].toString()),
                tipoNome = row[1].toString(),
                placa = row[3]?.toString(),
                descricao = row[4].toString(),
                medidorAtual = row[6].toString().toDouble(),
                motoristaId = UUID.fromString(row[7].toString()),
                motoristaNome = row[8].toString(),
                motoristaLogin = row[9].toString(),
                diarioAbertoEm = if (row[10] != null)
                    LocalDateTime.parse(row[10].toString().replace(" ", "T"))
                else null
            )
        }
    }

    fun buscarPorId(id: String): VeiculoResponse {
        val veiculo = veiculoRepository.findById(UUID.fromString(id))
            .orElseThrow { RuntimeException("Veículo não encontrado") }
        return VeiculoResponse(
            id = veiculo.id,
            tipoNome = "",
            tipoMedidor = "",
            placa = veiculo.placa,
            descricao = veiculo.descricao,
            status = veiculo.status,
            medidorAtual = veiculo.medidorAtual.toDouble()
        )
    }

    fun cadastrar(request: VeiculoRequest) {
        veiculoRepository.inserirVeiculo(
            tipoId = request.tipoId,
            placa = request.placa,
            descricao = request.descricao,
            medidorAtual = request.medidorAtual
        )
    }

    fun atualizar(id: String, request: VeiculoRequest) {
        veiculoRepository.findById(UUID.fromString(id))
            .orElseThrow { RuntimeException("Veículo não encontrado") }
        veiculoRepository.atualizarVeiculo(
            id = id,
            placa = request.placa,
            descricao = request.descricao
        )
    }

    fun inativar(id: String) {
        veiculoRepository.findById(UUID.fromString(id))
            .orElseThrow { RuntimeException("Veículo não encontrado") }
        veiculoRepository.inativarVeiculo(id)
    }
}