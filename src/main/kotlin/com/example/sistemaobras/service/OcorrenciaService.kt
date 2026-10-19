package com.example.sistemaobras.service

import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.dto.ManutencaoVinculadaResponse
import com.example.sistemaobras.dto.OcorrenciaDetalhadaResponse
import com.example.sistemaobras.dto.OcorrenciaRequest
import com.example.sistemaobras.dto.OcorrenciaResponse
import com.example.sistemaobras.entity.Manutencao
import com.example.sistemaobras.entity.Ocorrencia
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.ManutencaoRepository
import com.example.sistemaobras.repository.OcorrenciaRepository
import com.example.sistemaobras.repository.UsuarioRepository
import com.example.sistemaobras.repository.VeiculoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class OcorrenciaService(
    private val ocorrenciaRepository: OcorrenciaRepository,
    private val diarioBordoRepository: DiarioBordoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val veiculoRepository: VeiculoRepository,
    private val manutencaoRepository: ManutencaoRepository,
    private val logService: LogService
) {

    @Transactional
    fun registrar(request: OcorrenciaRequest): OcorrenciaResponse {
        val diario = diarioBordoRepository.findDiarioAberto(request.loginMotorista)
            ?: throw RegraNegocioException("Motorista não tem diário aberto")

        val registradoEm = if (request.registradoEm != null)
            // registradoEm é um instante absoluto (epoch em ms): o banco guarda em UTC.
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(request.registradoEm), java.time.ZoneOffset.UTC)
        else
            java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)

        val ocorrencia = Ocorrencia(
            diarioId = diario.id,
            tipo = request.tipo,
            descricao = request.descricao,
            latitude = request.latitude?.let { BigDecimal.valueOf(it) },
            longitude = request.longitude?.let { BigDecimal.valueOf(it) },
            registradoEm = registradoEm
        )

        val salva = ocorrenciaRepository.save(ocorrencia)

        logService.registrar(
            usuarioLogin = request.loginMotorista,
            usuarioNome = null,
            acao = "OCORRENCIA",
            descricao = "Ocorrência registrada — Tipo: ${request.tipo}, Descrição: ${request.descricao}"
        )

        return toResponse(salva)
    }

    // Gestor muda o status à mão (ex.: ocorrência resolvida na hora, sem manutenção).
    @Transactional
    fun atualizarStatus(id: String, status: String): OcorrenciaResponse {
        val ocorrencia = buscar(UUID.fromString(id))
        return toResponse(mudarStatus(ocorrencia, status, "manualmente"))
    }

    // Ocorrência existe? Usado antes de criar manutenção vinculada.
    fun buscar(id: UUID): Ocorrencia =
        ocorrenciaRepository.findById(id).orElseThrow { NaoEncontradoException("Ocorrência não encontrada.") }

    /**
     * Recalcula o status da ocorrência a partir das manutenções ligadas a ela.
     * `atual` é a manutenção que acabou de ser criada ou alterada (já com o novo status).
     * Todas concluídas -> concluida; senão -> em_andamento. Roda na transação de quem chamou.
     */
    fun atualizarPelaManutencao(atual: Manutencao) {
        val ocorrenciaId = atual.ocorrenciaId ?: return
        val ocorrencia = buscar(ocorrenciaId)
        val vinculadas = manutencaoRepository.findByOcorrenciaId(ocorrenciaId).filter { it.id != atual.id } + atual
        val novoStatus = if (vinculadas.all { it.status == "concluida" }) "concluida" else "em_andamento"
        mudarStatus(ocorrencia, novoStatus, "pela manutenção ${atual.id}")
    }

    private fun mudarStatus(ocorrencia: Ocorrencia, novoStatus: String, motivo: String): Ocorrencia {
        if (ocorrencia.status == novoStatus) return ocorrencia
        val resolvida = novoStatus == "concluida" || novoStatus == "descartada"
        val atualizada = ocorrenciaRepository.save(ocorrencia.copy(
            status = novoStatus,
            resolvidaEm = if (resolvida) LocalDateTime.now(ZoneOffset.UTC) else null
        ))
        logService.registrar(
            usuarioLogin = logService.loginAutenticado() ?: "sistema",
            usuarioNome = null,
            acao = "OCORRENCIA_STATUS",
            descricao = "Ocorrência ${ocorrencia.id} passou de '${ocorrencia.status}' para '$novoStatus' $motivo"
        )
        return atualizada
    }

    fun listarPorDiario(diarioId: String): List<OcorrenciaResponse> {
        return ocorrenciaRepository.findByDiarioId(UUID.fromString(diarioId)).map { toResponse(it) }
    }

    fun listarDiarioAberto(login: String): List<OcorrenciaResponse> {
        val diario = diarioBordoRepository.findDiarioAberto(login) ?: return emptyList()
        return ocorrenciaRepository.findByDiarioId(diario.id!!).map { toResponse(it) }
    }

    fun listarPorVeiculo(veiculoId: String): List<OcorrenciaDetalhadaResponse> {
        return ocorrenciaRepository.findByVeiculoId(UUID.fromString(veiculoId)).map { toDetalhadaResponse(it) }
    }

    fun listarPorMotorista(login: String): List<OcorrenciaDetalhadaResponse> {
        return ocorrenciaRepository.findByMotoristaLogin(login).map { toDetalhadaResponse(it) }
    }

    fun listarTodas(status: String? = null): List<OcorrenciaDetalhadaResponse> {
        val ocorrencias = if (status.isNullOrBlank())
            ocorrenciaRepository.findAll().sortedByDescending { it.registradoEm }
        else
            ocorrenciaRepository.findByStatusOrderByRegistradoEmDesc(status)
        return ocorrencias.map { toDetalhadaResponse(it) }
    }

    private fun toResponse(o: Ocorrencia) = OcorrenciaResponse(
        id = o.id,
        diarioId = o.diarioId,
        tipo = o.tipo,
        descricao = o.descricao,
        latitude = o.latitude?.toDouble(),
        longitude = o.longitude?.toDouble(),
        registradoEm = o.registradoEm,
        status = o.status,
        resolvidaEm = o.resolvidaEm
    )

    private fun toDetalhadaResponse(o: Ocorrencia): OcorrenciaDetalhadaResponse {
        val diario = diarioBordoRepository.findById(o.diarioId!!).orElseThrow()
        val usuario = usuarioRepository.findById(diario.usuarioId!!).orElseThrow()
        val veiculo = veiculoRepository.findById(diario.veiculoId!!).orElseThrow()
        val foto = try { usuarioRepository.findFotoByLogin(usuario.login) } catch (e: Exception) { null }
        return OcorrenciaDetalhadaResponse(
            id = o.id,
            diarioId = o.diarioId,
            tipo = o.tipo,
            descricao = o.descricao,
            latitude = o.latitude?.toDouble(),
            longitude = o.longitude?.toDouble(),
            registradoEm = o.registradoEm!!,
            motoristaNome = usuario.nomeCompleto ?: usuario.login,
            motoristaLogin = usuario.login,
            motoristaFoto = foto,
            veiculoId = veiculo.id,
            veiculoDescricao = veiculo.descricao,
            veiculoPlaca = veiculo.placa,
            status = o.status,
            resolvidaEm = o.resolvidaEm,
            manutencoes = manutencaoRepository.findByOcorrenciaId(o.id!!).map { ManutencaoVinculadaResponse(it.id, it.status) }
        )
    }
}
