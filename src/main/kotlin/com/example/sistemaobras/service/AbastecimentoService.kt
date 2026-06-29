package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AbastecimentoDetalhadoResponse
import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.AbastecimentoResponse
import com.example.sistemaobras.repository.AbastecimentoRepository
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.UsuarioRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AbastecimentoService(
    private val abastecimentoRepository: AbastecimentoRepository,
    private val diarioRepository: DiarioBordoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val logService: LogService
) {

    fun registrar(request: AbastecimentoRequest): AbastecimentoResponse {
        if (request.veiculoAbastecidoId != null) {
            val temDiario = diarioRepository.temDiarioAbertoPorVeiculo(request.veiculoAbastecidoId)
            if (temDiario == 0L)
                throw RuntimeException("Veículo a ser abastecido não tem diário aberto")
        } else {
            if (diarioRepository.temDiarioAberto(request.loginMotorista) == 0L)
                throw RuntimeException("Motorista não tem diário aberto")
        }

        val registradoEm = if (request.registradoEm != null)
            java.time.Instant.ofEpochMilli(request.registradoEm)
                .atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .toLocalDateTime()
        else
            java.time.LocalDateTime.now()

        abastecimentoRepository.inserirAbastecimento(
            login = request.loginMotorista,
            tipoCombustivel = request.tipoCombustivel,
            litros = request.litros,
            valorTotal = request.valorTotal,
            posto = request.posto,
            veiculoAbastecidoId = request.veiculoAbastecidoId,
            registradoEm = registradoEm
        )

        val descLog = "Abastecimento registrado — ${request.tipoCombustivel}, " +
                "${"%.1f".format(request.litros)} L, R$ ${"%.2f".format(request.valorTotal)}" +
                if (!request.posto.isNullOrEmpty()) ", Posto: ${request.posto}" else "" +
                        if (!request.veiculoAbastecidoId.isNullOrEmpty()) " (Comboio)" else ""

        logService.registrar(
            usuarioLogin = request.loginMotorista,
            usuarioNome = null,
            acao = "ABASTECIMENTO",
            descricao = descLog
        )

        return AbastecimentoResponse(
            id = null,
            tipoCombustivel = request.tipoCombustivel,
            litros = request.litros,
            valorTotal = request.valorTotal,
            posto = request.posto,
            registradoEm = registradoEm
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

    private fun mapearRow(row: Array<Any>): AbastecimentoDetalhadoResponse {
        val motoristaLogin = row[12].toString()
        val foto = try { usuarioRepository.findFotoByLogin(motoristaLogin) } catch (e: Exception) { null }

        return AbastecimentoDetalhadoResponse(
            id = UUID.fromString(row[0].toString()),
            tipoCombustivel = row[2].toString(),
            litros = row[3].toString().toDouble(),
            valorTotal = row[4].toString().toDouble(),
            posto = row[5]?.toString(),
            registradoEm = if (row[7] != null)
                java.time.LocalDateTime.parse(row[7].toString().replace(" ", "T"))
            else null,
            veiculoAbastecidoId = row[8]?.toString(),
            veiculoId = row[9].toString(),
            veiculoDescricao = row[10].toString(),
            veiculoPlaca = row[11]?.toString(),
            motoristaLogin = motoristaLogin,
            motoristaNome = row[13].toString(),
            motoristaFoto = foto,
            veiculoAbastecidoDescricao = row[14]?.toString()
        )
    }

    fun listarTodos(): List<AbastecimentoDetalhadoResponse> {
        return abastecimentoRepository.findTodos().map { mapearRow(it) }
    }

    fun listarPorVeiculo(veiculoId: String): List<AbastecimentoDetalhadoResponse> {
        return abastecimentoRepository.findByVeiculo(veiculoId).map { mapearRow(it) }
    }

    fun listarPorMotorista(login: String): List<AbastecimentoDetalhadoResponse> {
        return abastecimentoRepository.findByMotorista(login).map { mapearRow(it) }
    }
}