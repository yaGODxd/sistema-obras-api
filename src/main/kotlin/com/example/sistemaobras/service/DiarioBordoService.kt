package com.example.sistemaobras.service

import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.exception.uuidValido
import com.example.sistemaobras.dto.AbrirDiarioRequest
import com.example.sistemaobras.dto.DiarioDetalheResponse
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.dto.DiarioResponse
import com.example.sistemaobras.dto.DiarioResumoMensalResponse
import com.example.sistemaobras.dto.FecharDiarioRequest
import com.example.sistemaobras.repository.AssinaturaRepository
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.TurnoRepository
import com.example.sistemaobras.repository.VeiculoRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DiarioBordoService(
    private val diarioRepository: DiarioBordoRepository,
    private val turnoRepository: TurnoRepository,
    private val veiculoRepository: VeiculoRepository,
    private val logService: LogService,
    private val assinaturaRepository: AssinaturaRepository,
    private val diarioDoEvento: DiarioDoEvento,
    // APP_BLOQUEIOS_DIARIO=false desliga os bloqueios dos itens 9 e 15 (motorista travado).
    @Value("\${app.bloqueios.diario:true}") private val bloqueiosAtivos: Boolean = true
) {

    // Diário, status do veículo e log: tudo ou nada (o log tem transação própria).
    // Parte 6B: aberto_em = hora do evento (ocorridoEm); reenvio com o mesmo idLocal devolve o diário já aberto.
    @Transactional
    fun abrirDiario(request: AbrirDiarioRequest): DiarioResponse {
        val login = request.loginMotorista
        val idLocal = request.idLocal?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        idLocal?.let { diarioDoEvento.porIdLocalAbertura(it) }?.let { return jaRecebido(it, login) }

        if (turnoRepository.temTurnoAberto(login) == 0L)
            throw RegraNegocioException("Motorista não tem turno aberto")

        if (diarioRepository.temDiarioAberto(login) > 0)
            throw RegraNegocioException("Motorista já tem um diário aberto")

        // Item 9: bloqueios que antes só o app fazia (interruptor APP_BLOQUEIOS_DIARIO).
        if (bloqueiosAtivos) {
            if (assinaturaRepository.contarPinDefinido(login) == 0L)
                throw RegraNegocioException("Cadastre seu PIN de assinatura antes de abrir um diário.", CodigosErro.PIN_AUSENTE)
            if (diarioRepository.contarPendentesDeAssinatura(login) > 0)
                throw RegraNegocioException(
                    "Você tem um diário fechado aguardando assinatura. Assine-o antes de abrir outro.",
                    CodigosErro.DIARIO_PENDENTE_ASSINATURA
                )
        }

        var horario = HorarioEvento.resolver(request.ocorridoEm, null, request.enviadoEm)
        diarioRepository.aberturaDoTurnoAberto(login).firstOrNull()?.let {
            horario = horario.conferirJanela(dataHora(it)!!, null, CodigosErro.FORA_DA_JANELA_TURNO)
        }

        diarioRepository.abrirDiario(
            login = login,
            veiculoId = request.veiculoId,
            motivoUsoId = request.motivoUsoId,
            destino = request.destino,
            medidorInicial = request.medidorInicial,
            abertoEm = horario.ocorrido,
            recebidoEm = horario.recebido,
            idLocal = idLocal?.toString(),
            desvio = horario.desvioSeg,
            suspeito = horario.suspeito,
            motivo = horario.motivoTexto
        )

        val diario = diarioRepository.findDiarioAberto(login)!!

        val veiculo = try {
            veiculoRepository.findById(java.util.UUID.fromString(request.veiculoId)).orElse(null)
        } catch (e: Exception) { null }

        // Marca o veículo como em_uso (o trigger fn_abre_diario também faz isso)
        veiculoRepository.atualizarStatus(request.veiculoId, "em_uso")

        logService.registrar(
            usuarioLogin = login,
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
            motoristaNome = login,
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

    // POST /diarios/fechar/{login} (rota antiga). Reenvio com o mesmo idLocal devolve o diário já fechado.
    @Transactional
    fun fecharDiario(login: String, request: FecharDiarioRequest): DiarioResponse {
        val idLocal = request.idLocal?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        idLocal?.let { diarioDoEvento.porIdLocalFechamento(it) }?.let { return jaRecebido(it, login) }

        val aberto = diarioRepository.findDiarioAberto(login)
            ?: throw RegraNegocioException("Não há diário aberto para este motorista", CodigosErro.SEM_DIARIO_ABERTO)
        return fechar(diarioDoEvento.porId(aberto.id.toString()), request, idLocal)
    }

    // POST /diarios/{id}/fechar (rota nova, Parte 6B): 404 diário não existe, 403 de outro motorista,
    // 409 DIARIO_JA_FECHADO (já fechado por outro envio) ou ID_LOCAL_EM_USO.
    @Transactional
    fun fecharDiarioPorId(id: String, request: FecharDiarioRequest): DiarioResponse {
        val diario = diarioDoEvento.porId(id)
        diarioDoEvento.conferirDono(diario)
        val idLocal = request.idLocal?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        if (idLocal != null) {
            if (diario.idLocalFechamento == idLocal) return respostaPorId(diario.id)
            diarioDoEvento.porIdLocalFechamento(idLocal)?.let {
                throw ConflitoException("Este idLocal já foi usado em outro registro.", CodigosErro.ID_LOCAL_EM_USO)
            }
        }
        if (!diario.aberto)
            throw ConflitoException("Este diário já foi fechado.", CodigosErro.DIARIO_JA_FECHADO)
        return fechar(diario, request, idLocal)
    }

    private fun fechar(diario: DiarioDoEvento.Diario, request: FecharDiarioRequest, idLocal: java.util.UUID?): DiarioResponse {
        // Troca de hodômetro não é tratada (limitação documentada).
        if (request.medidorFinal < diario.medidorInicial)
            throw RegraNegocioException("O medidor final não pode ser menor que o medidor inicial.")

        val horario = HorarioEvento.resolver(request.ocorridoEm, null, request.enviadoEm)
            .conferirJanela(diario.abertoEm, null, CodigosErro.FORA_DA_JANELA_DIARIO)

        val alterados = diarioRepository.fecharDiario(
            id = diario.id.toString(),
            medidorFinal = request.medidorFinal,
            observacao = request.observacaoFechamento,
            fechadoEm = horario.ocorrido,
            recebidoEm = horario.recebido,
            idLocal = idLocal?.toString(),
            desvio = horario.desvioSeg,
            suspeito = horario.suspeito,
            motivo = horario.motivoTexto
        )
        // Outro envio fechou o diário entre a leitura e o UPDATE.
        if (alterados == 0)
            throw ConflitoException("Este diário já foi fechado.", CodigosErro.DIARIO_JA_FECHADO)

        // O status do veículo (e o medidor_atual) é atualizado pelo trigger fn_fecha_diario
        // no próprio UPDATE. Não sobrescrever aqui: a versão nova do trigger (Parte 6B)
        // deixa em_manutencao quando há manutenção aberta.

        val percorrido = request.medidorFinal - diario.medidorInicial

        logService.registrar(
            usuarioLogin = diario.dono,
            usuarioNome = null,
            acao = "FECHAR_DIARIO",
            descricao = "Diário fechado — Medidor final: ${request.medidorFinal}, Percorrido: ${"%.1f".format(percorrido)}"
        )

        return DiarioResponse(
            id = diario.id,
            veiculoDescricao = "",
            veiculoPlaca = null,
            motoristaNome = diario.dono,
            medidorInicial = diario.medidorInicial,
            medidorFinal = request.medidorFinal,
            medidorPercorrido = percorrido,
            destino = null,
            status = "fechado",
            abertoEm = diario.abertoEm,
            fechadoEm = horario.ocorrido,
            veiculoId = diario.veiculoId,
            tipoMedidor = tipoMedidor(diario.veiculoId)
        )
    }

    // Reenvio: devolve o diário já gravado se for do mesmo motorista; senão 409 ID_LOCAL_EM_USO.
    private fun jaRecebido(diario: DiarioDoEvento.Diario, login: String): DiarioResponse {
        if (diario.dono != login)
            throw ConflitoException("Este idLocal já foi usado em outro registro.", CodigosErro.ID_LOCAL_EM_USO)
        return respostaPorId(diario.id)
    }

    private fun respostaPorId(id: java.util.UUID): DiarioResponse =
        linhaParaDiario(diarioRepository.buscarDetalhe(id.toString()).first())
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