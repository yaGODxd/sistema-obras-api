package com.example.sistemaobras.service

import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.dto.AbrirDiarioRequest
import com.example.sistemaobras.dto.DiarioDetalheResponse
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.dto.DiarioResponse
import com.example.sistemaobras.dto.DiarioResumoMensalResponse
import com.example.sistemaobras.dto.FecharDiarioRequest
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.TurnoRepository
import com.example.sistemaobras.repository.VeiculoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DiarioBordoService(
    private val diarioRepository: DiarioBordoRepository,
    private val turnoRepository: TurnoRepository,
    private val veiculoRepository: VeiculoRepository,
    private val logService: LogService
) {

    // Diário, status do veículo e log: tudo ou nada (o log tem transação própria).
    @Transactional
    fun abrirDiario(request: AbrirDiarioRequest): DiarioResponse {
        if (turnoRepository.temTurnoAberto(request.loginMotorista) == 0L)
            throw RegraNegocioException("Motorista não tem turno aberto")

        if (diarioRepository.temDiarioAberto(request.loginMotorista) > 0)
            throw RegraNegocioException("Motorista já tem um diário aberto")

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

        // Marca o veículo como em_uso
        veiculoRepository.atualizarStatus(request.veiculoId, "em_uso")

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
            fechadoEm = null,
            veiculoId = diario.veiculoId,
            tipoMedidor = tipoMedidor(diario.veiculoId)
        )
    }

    @Transactional
    fun fecharDiario(login: String, request: FecharDiarioRequest): DiarioResponse {
        val diario = diarioRepository.findDiarioAberto(login)
            ?: throw RegraNegocioException("Não há diário aberto para este motorista")

        // Troca de hodômetro não é tratada (limitação documentada).
        if (request.medidorFinal < diario.medidorInicial.toDouble())
            throw RegraNegocioException("O medidor final não pode ser menor que o medidor inicial.")

        diarioRepository.fecharDiario(
            id = diario.id.toString(),
            medidorFinal = request.medidorFinal,
            observacao = request.observacaoFechamento
        )

        // Devolve o veículo para disponivel ao fechar o diário
        veiculoRepository.atualizarStatus(diario.veiculoId.toString(), "disponivel")

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
            fechadoEm = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC),
            veiculoId = diario.veiculoId,
            tipoMedidor = tipoMedidor(diario.veiculoId)
        )
    }

    fun buscarDiarioAberto(login: String): DiarioResponse? {
        return try {
            val rows = diarioRepository.findDiarioAbertoDetalhado(login)
            if (rows.isEmpty()) return null
            linhaParaDiario(rows[0])
        } catch (e: Exception) {
            println("ERRO buscarDiarioAberto: ${e.message}")
            null
        }
    }

    fun listarDiarios(veiculoId: String?, mes: Int?, ano: Int?): List<DiarioResponse> {
        return diarioRepository.findDiariosPorFiltro(veiculoId, mes, ano).map { linhaParaDiario(it) }
    }

    // GET /diarios/{id}. Rota nova: id inválido -> 400, não encontrado -> 404.
    fun buscarDetalhe(id: String): DiarioDetalheResponse {
        try {
            java.util.UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw RegraNegocioException("Identificador inválido.")
        }
        val r = diarioRepository.buscarDetalhe(id).firstOrNull()
            ?: throw NaoEncontradoException("Diário não encontrado.")
        val base = linhaParaDiario(r)
        return DiarioDetalheResponse(
            id = base.id,
            veiculoDescricao = base.veiculoDescricao,
            veiculoPlaca = base.veiculoPlaca,
            motoristaLogin = r[17]?.toString(),
            motoristaNome = base.motoristaNome,
            medidorInicial = base.medidorInicial,
            medidorFinal = base.medidorFinal,
            medidorPercorrido = base.medidorPercorrido,
            destino = base.destino,
            motivoUsoId = r[13]?.toString(),
            observacaoFechamento = r[15]?.toString(),
            status = base.status,
            abertoEm = base.abertoEm,
            fechadoEm = base.fechadoEm,
            assinadoEm = dataHora(r[16]),
            veiculoId = base.veiculoId,
            tipoMedidor = base.tipoMedidor,
            motivoUso = r[14]?.toString()
        )
    }

    // Linha das consultas de diário (índices 0-12, ver DiarioBordoRepository).
    private fun linhaParaDiario(row: Array<out Any?>) = DiarioResponse(
        id = java.util.UUID.fromString(row[0].toString()),
        veiculoDescricao = row[1]?.toString() ?: "",
        veiculoPlaca = row[2]?.toString(),
        motoristaNome = row[3]?.toString() ?: "",
        medidorInicial = row[4].toString().toDouble(),
        medidorFinal = row[5]?.toString()?.toDoubleOrNull(),
        medidorPercorrido = row[6]?.toString()?.toDoubleOrNull(),
        destino = row[7]?.toString(),
        status = row[8]?.toString() ?: "",
        abertoEm = dataHora(row[9]),
        fechadoEm = dataHora(row[10]),
        veiculoId = row[11]?.let { java.util.UUID.fromString(it.toString()) },
        tipoMedidor = row[12]?.toString()
    )

    private fun dataHora(x: Any?): java.time.LocalDateTime? =
        x?.let { java.time.LocalDateTime.parse(it.toString().replace(" ", "T")) }

    // Tipo de medidor do veículo, para as respostas de abrir e fechar diário.
    private fun tipoMedidor(veiculoId: java.util.UUID?): String? {
        val tipoId = veiculoId?.let { veiculoRepository.findById(it).orElse(null) }?.tipoId ?: return null
        return veiculoRepository.listarTipos().firstOrNull { it[0].toString() == tipoId.toString() }?.get(2)?.toString()
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