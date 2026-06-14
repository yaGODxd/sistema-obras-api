package com.example.sistemaobras.service

import com.example.sistemaobras.dto.OcorrenciaDetalhadaResponse
import com.example.sistemaobras.dto.OcorrenciaRequest
import com.example.sistemaobras.dto.OcorrenciaResponse
import com.example.sistemaobras.entity.Ocorrencia
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.OcorrenciaRepository
import com.example.sistemaobras.repository.UsuarioRepository
import com.example.sistemaobras.repository.VeiculoRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class OcorrenciaService(
    private val ocorrenciaRepository: OcorrenciaRepository,
    private val diarioBordoRepository: DiarioBordoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val veiculoRepository: VeiculoRepository
) {

    fun registrar(request: OcorrenciaRequest): OcorrenciaResponse {
        val diario = diarioBordoRepository.findDiarioAberto(request.loginMotorista)
            ?: throw RuntimeException("Diário aberto não encontrado para o motorista ${request.loginMotorista}")

        val ocorrencia = Ocorrencia(
            diarioId = diario.id,
            tipo = request.tipo,
            descricao = request.descricao,
            latitude = request.latitude?.let { BigDecimal.valueOf(it) },
            longitude = request.longitude?.let { BigDecimal.valueOf(it) }
        )

        val salva = ocorrenciaRepository.save(ocorrencia)
        return toResponse(salva)
    }

    fun listarPorDiario(diarioId: String): List<OcorrenciaResponse> {
        return ocorrenciaRepository.findByDiarioId(UUID.fromString(diarioId))
            .map { toResponse(it) }
    }

    fun listarDiarioAberto(login: String): List<OcorrenciaResponse> {
        val diario = diarioBordoRepository.findDiarioAberto(login)
            ?: return emptyList()
        return ocorrenciaRepository.findByDiarioId(diario.id!!)
            .map { toResponse(it) }
    }

    fun listarPorVeiculo(veiculoId: String): List<OcorrenciaDetalhadaResponse> {
        return ocorrenciaRepository.findByVeiculoId(UUID.fromString(veiculoId))
            .map { toDetalhadaResponse(it) }
    }

    fun listarPorMotorista(login: String): List<OcorrenciaDetalhadaResponse> {
        return ocorrenciaRepository.findByMotoristaLogin(login)
            .map { toDetalhadaResponse(it) }
    }

    fun listarTodas(): List<OcorrenciaDetalhadaResponse> {
        return ocorrenciaRepository.findAll()
            .sortedByDescending { it.registradoEm }
            .map { toDetalhadaResponse(it) }
    }

    private fun toResponse(o: Ocorrencia) = OcorrenciaResponse(
        id = o.id,
        diarioId = o.diarioId,
        tipo = o.tipo,
        descricao = o.descricao,
        latitude = o.latitude?.toDouble(),
        longitude = o.longitude?.toDouble(),
        registradoEm = o.registradoEm
    )

    private fun toDetalhadaResponse(o: Ocorrencia): OcorrenciaDetalhadaResponse {
        val diario = diarioBordoRepository.findById(o.diarioId!!).orElseThrow()
        val usuario = usuarioRepository.findById(diario.usuarioId!!).orElseThrow()
        val veiculo = veiculoRepository.findById(diario.veiculoId!!).orElseThrow()
        return OcorrenciaDetalhadaResponse(
            id = o.id,
            diarioId = o.diarioId,
            tipo = o.tipo,
            descricao = o.descricao,
            latitude = o.latitude?.toDouble(),
            longitude = o.longitude?.toDouble(),
            registradoEm = o.registradoEm,
            motoristaNome = usuario.nomeCompleto ?: usuario.login,
            motoristaLogin = usuario.login,
            veiculoId = veiculo.id,
            veiculoDescricao = veiculo.descricao,
            veiculoPlaca = veiculo.placa
        )
    }
}