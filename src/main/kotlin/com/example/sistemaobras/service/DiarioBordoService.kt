package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AbrirDiarioRequest
import com.example.sistemaobras.dto.DiarioResponse
import com.example.sistemaobras.dto.DiarioResumoMensalResponse
import com.example.sistemaobras.dto.FecharDiarioRequest
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.TurnoRepository
import com.example.sistemaobras.repository.VeiculoRepository
import org.springframework.stereotype.Service

@Service
class DiarioBordoService(
    private val diarioRepository: DiarioBordoRepository,
    private val turnoRepository: TurnoRepository,
    private val veiculoRepository: VeiculoRepository,
    private val logService: LogService
) {

    fun abrirDiario(request: AbrirDiarioRequest): DiarioResponse {
        if (turnoRepository.temTurnoAberto(request.loginMotorista) == 0L)
            throw RuntimeException("Motorista não tem turno aberto")

        if (diarioRepository.temDiarioAberto(request.loginMotorista) > 0)
            throw RuntimeException("Motorista já tem um diário aberto")

        diarioRepository.abrirDiario(
            login = request.loginMotorista,
            veiculoId = request.veiculoId,
            motivoUsoId = request.motivoUsoId,
            destino = request.destino,
            medidorInicial = request.medidorInicial
        )

        val diario = diarioRepository.findDiarioAberto(request.loginMotorista)!!

        val veiculo = try {
            veiculoRepository.findById(java.util.UUID.fromString(request.veiculoId)).orElse(null)
        } catch (e: Exception) { null }

        logService.registrar(
            usuarioLogin = request.loginMotorista,
            usuarioNome = null,
            acao = "ABRIR_DIARIO",
            descricao = "Diário aberto — Veículo: ${veiculo?.descricao ?: request.veiculoId}" +
                    if (!request.destino.isNullOrEmpty()) ", Destino: ${request.destino}" else "" +
                            if (!request.observacao.isNullOrEmpty()) " | ${request.observacao}" else ""
        )

        return DiarioResponse(
            id = diario.id,
            veiculoDescricao = veiculo?.descricao ?: "",
            veiculoPlaca = veiculo?.placa,
            motoristaNome = request.loginMotorista,
            medidorInicial = diario.medidorInicial.toDouble(),
            medidorFinal = null,
            medidorPercorrido = null,
            destino = diario.destino,
            status = diario.status,
            abertoEm = diario.abertoEm,
            fechadoEm = null
        )
    }

    fun fecharDiario(login: String, request: FecharDiarioRequest): DiarioResponse {
        val diario = diarioRepository.findDiarioAberto(login)
            ?: throw RuntimeException("Não há diário aberto para este motorista")

        diarioRepository.fecharDiario(
            id = diario.id.toString(),
            medidorFinal = request.medidorFinal,
            observacao = request.observacaoFechamento
        )

        val percorrido = request.medidorFinal - diario.medidorInicial.toDouble()

        logService.registrar(
            usuarioLogin = login,
            usuarioNome = null,
            acao = "FECHAR_DIARIO",
            descricao = "Diário fechado — Medidor final: ${request.medidorFinal}, Percorrido: ${"%.1f".format(percorrido)}"
        )

        return DiarioResponse(
            id = diario.id,
            veiculoDescricao = "",
            veiculoPlaca = null,
            motoristaNome = login,
            medidorInicial = diario.medidorInicial.toDouble(),
            medidorFinal = request.medidorFinal,
            medidorPercorrido = percorrido,
            destino = diario.destino,
            status = "fechado",
            abertoEm = diario.abertoEm,
            fechadoEm = java.time.LocalDateTime.now()
        )
    }

    fun buscarDiarioAberto(login: String): DiarioResponse? {
        return try {
            val rows = diarioRepository.findDiarioAbertoDetalhado(login)
            if (rows.isEmpty()) return null
            val row = rows[0]
            DiarioResponse(
                id = java.util.UUID.fromString(row[0].toString()),
                veiculoDescricao = row[1]?.toString() ?: "",
                veiculoPlaca = row[2]?.toString(),
                motoristaNome = row[3]?.toString() ?: "",
                medidorInicial = row[4].toString().toDouble(),
                medidorFinal = row[5]?.toString()?.toDoubleOrNull(),
                medidorPercorrido = row[6]?.toString()?.toDoubleOrNull(),
                destino = row[7]?.toString(),
                status = row[8]?.toString() ?: "",
                abertoEm = if (row[9] != null)
                    java.time.LocalDateTime.parse(row[9].toString().replace(" ", "T"))
                else null,
                fechadoEm = if (row[10] != null)
                    java.time.LocalDateTime.parse(row[10].toString().replace(" ", "T"))
                else null
            )
        } catch (e: Exception) {
            println("ERRO buscarDiarioAberto: ${e.message}")
            null
        }
    }

    fun listarDiarios(veiculoId: String?, mes: Int?, ano: Int?): List<DiarioResponse> {
        return diarioRepository.findDiariosPorFiltro(veiculoId, mes, ano).map { row ->
            DiarioResponse(
                id = java.util.UUID.fromString(row[0].toString()),
                veiculoDescricao = row[1]?.toString() ?: "",
                veiculoPlaca = row[2]?.toString(),
                motoristaNome = row[3]?.toString() ?: "",
                medidorInicial = row[4].toString().toDouble(),
                medidorFinal = row[5]?.toString()?.toDoubleOrNull(),
                medidorPercorrido = row[6]?.toString()?.toDoubleOrNull(),
                destino = row[7]?.toString(),
                status = row[8]?.toString() ?: "",
                abertoEm = if (row[9] != null)
                    java.time.LocalDateTime.parse(row[9].toString().replace(" ", "T"))
                else null,
                fechadoEm = if (row[10] != null)
                    java.time.LocalDateTime.parse(row[10].toString().replace(" ", "T"))
                else null
            )
        }
    }

    fun listarResumoMensal(veiculoId: String?, mes: Int?, ano: Int?): List<DiarioResumoMensalResponse> {
        return diarioRepository.findResumoMensal(veiculoId, mes, ano).map { row ->
            DiarioResumoMensalResponse(
                veiculoId = row[0].toString(),
                veiculoDescricao = row[1].toString(),
                veiculoPlaca = row[2]?.toString(),
                mes = row[3].toString().toDouble().toInt(),
                ano = row[4].toString().toDouble().toInt(),
                totalDiarios = row[5].toString().toDouble().toInt(),
                primeiroMedidor = row[6].toString().toDouble(),
                ultimoMedidor = row[7].toString().toDouble(),
                totalPercorrido = row[8].toString().toDouble()
            )
        }
    }
}