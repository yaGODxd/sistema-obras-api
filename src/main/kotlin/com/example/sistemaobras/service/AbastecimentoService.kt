package com.example.sistemaobras.service

import com.example.sistemaobras.entity.Abastecimento
import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.exception.uuidValido
import com.example.sistemaobras.security.ControleAcesso
import com.example.sistemaobras.dto.AbastecimentoDetalhadoResponse
import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.AbastecimentoResponse
import com.example.sistemaobras.repository.AbastecimentoRepository
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.UsuarioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AbastecimentoService(
    private val abastecimentoRepository: AbastecimentoRepository,
    private val diarioRepository: DiarioBordoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val logService: LogService,
    private val diarioDoEvento: DiarioDoEvento
) {

    /**
     * Parte 6B. Qual diário recebe o abastecimento:
     *  - diarioId informado: esse diário (do próprio motorista; no comboio, do veículo abastecido);
     *  - comboio sem diarioId: o diário do veículo abastecido no HORÁRIO DO EVENTO (pode já estar fechado);
     *  - senão: o diário aberto agora do motorista (como antes).
     * Diário assinado recusa (409). Fora do período do diário só marca como suspeito.
     */
    @Transactional
    fun registrar(request: AbastecimentoRequest): AbastecimentoResponse {
        val login = request.loginMotorista
        val idLocal = request.idLocal?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        idLocal?.let { jaRecebido(it, login) }?.let { return it }

        var horario = HorarioEvento.resolver(request.ocorridoEm, request.registradoEm, request.enviadoEm)
        val comboio = !request.veiculoAbastecidoId.isNullOrEmpty()

        val diario = when {
            !request.diarioId.isNullOrBlank() -> diarioDoEvento.porId(request.diarioId).also {
                if (comboio) {
                    if (it.veiculoId.toString() != request.veiculoAbastecidoId)
                        throw RegraNegocioException("O diário informado não é do veículo abastecido.")
                } else {
                    diarioDoEvento.conferirDono(it)
                }
            }
            comboio -> diarioDoEvento.porVeiculoNoHorario(request.veiculoAbastecidoId!!, horario.ocorrido)
                ?: throw RegraNegocioException("Veículo a ser abastecido não tem diário aberto", CodigosErro.DIARIO_NAO_ENCONTRADO)
            else -> diarioRepository.findDiarioAberto(login)?.let { diarioDoEvento.porId(it.id.toString()) }
                ?: throw RegraNegocioException("Motorista não tem diário aberto", CodigosErro.SEM_DIARIO_ABERTO)
        }
        diarioDoEvento.conferirNaoAssinado(diario)
        horario = horario.conferirJanela(diario.abertoEm, diario.fechadoEm, CodigosErro.FORA_DA_JANELA_DIARIO)
        // Comboio: quem registra não é o dono do diário. Regra proposta: marcar quando o
        // motorista não dirigia um veículo de comboio no horário do evento.
        if (comboio && !diarioDoEvento.motoristaDirigiaComboio(login, horario.ocorrido))
            horario = horario.comMotivo(CodigosErro.COMBOIO_SEM_DIARIO)

        val id = UUID.randomUUID()
        abastecimentoRepository.inserirAbastecimento(
            id = id.toString(),
            diarioId = diario.id.toString(),
            tipoCombustivel = request.tipoCombustivel,
            litros = request.litros,
            valorTotal = request.valorTotal,
            posto = request.posto,
            veiculoAbastecidoId = request.veiculoAbastecidoId?.takeIf { it.isNotEmpty() },
            registradoEm = horario.ocorrido,
            recebidoEm = horario.recebido,
            idLocal = idLocal?.toString(),
            desvio = horario.desvioSeg,
            suspeito = horario.suspeito,
            motivo = horario.motivoTexto,
            registradoPor = ControleAcesso.loginDoToken()
        )

        // Parênteses: sem eles o " (Comboio)" sumia quando havia posto.
        val descLog = "Abastecimento registrado — ${request.tipoCombustivel}, " +
                "${"%.1f".format(request.litros)} L, R$ ${"%.2f".format(request.valorTotal)}" +
                (if (!request.posto.isNullOrEmpty()) ", Posto: ${request.posto}" else "") +
                (if (comboio) " (Comboio)" else "")

        logService.registrar(
            usuarioLogin = login,
            usuarioNome = null,
            acao = "ABASTECIMENTO",
            descricao = descLog
        )

        return AbastecimentoResponse(
            id = id,
            tipoCombustivel = request.tipoCombustivel,
            litros = request.litros,
            valorTotal = request.valorTotal,
            posto = request.posto,
            registradoEm = horario.ocorrido,
            comboio = comboio,
            veiculoAbastecidoId = request.veiculoAbastecidoId?.takeIf { it.isNotEmpty() }?.let { UUID.fromString(it) },
            recebidoEm = horario.recebido,
            idLocal = idLocal,
            horarioSuspeito = horario.suspeito,
            motivoSuspeita = horario.motivoTexto
        )
    }

    // Reenvio com o mesmo idLocal: devolve o já gravado se for do dono do diário ou de quem registrou.
    private fun jaRecebido(idLocal: UUID, login: String): AbastecimentoResponse? {
        val linha = abastecimentoRepository.buscarPorIdLocal(idLocal.toString()).firstOrNull() ?: return null
        val dono = linha[1]?.toString()
        val quemRegistrou = linha[2]?.toString()
        if (login != dono && login != quemRegistrou)
            throw ConflitoException("Este idLocal já foi usado em outro registro.", CodigosErro.ID_LOCAL_EM_USO)
        return abastecimentoRepository.findById(UUID.fromString(linha[0].toString())).map { resposta(it) }.orElse(null)
    }

    private fun resposta(a: Abastecimento) = AbastecimentoResponse(
        id = a.id,
        tipoCombustivel = a.tipoCombustivel,
        litros = a.litros.toDouble(),
        valorTotal = a.valorTotal.toDouble(),
        posto = a.posto,
        registradoEm = a.registradoEm,
        comboio = a.veiculoAbastecidoId != null,
        veiculoAbastecidoId = a.veiculoAbastecidoId,
        recebidoEm = a.recebidoEm,
        idLocal = a.idLocal,
        horarioSuspeito = a.horarioSuspeito,
        motivoSuspeita = a.motivoSuspeita
    )
    fun listarPorDiario(diarioId: String): List<AbastecimentoResponse> {
        return abastecimentoRepository.findByDiarioId(diarioId).map { resposta(it) }
    }

    fun listarDiarioAberto(login: String): List<AbastecimentoResponse> {
        return abastecimentoRepository.findByDiarioAberto(login).map { resposta(it) }
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