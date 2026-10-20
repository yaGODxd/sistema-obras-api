package com.example.sistemaobras.service

import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.exception.uuidValido
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

    private data class TipoVeiculo(val nome: String, val medidor: String)

    // Uma consulta para todos os tipos (em vez de uma por veículo).
    private fun tiposPorId(): Map<String, TipoVeiculo> =
        veiculoRepository.listarTipos().associate { row ->
            row[0].toString() to TipoVeiculo(row[1]?.toString() ?: "", row[2]?.toString() ?: "")
        }

    private fun toResponse(v: com.example.sistemaobras.entity.Veiculo, tipos: Map<String, TipoVeiculo>): VeiculoResponse {
        val tipo = tipos[v.tipoId.toString()]
        return VeiculoResponse(
            id = v.id,
            tipoId = v.tipoId,
            tipoNome = tipo?.nome ?: "",
            tipoMedidor = tipo?.medidor ?: "",
            placa = v.placa,
            descricao = v.descricao,
            status = v.status,
            medidorAtual = v.medidorAtual.toDouble(),
            marca = v.marca,
            modelo = v.modelo,
            ano = v.ano,
            cor = v.cor,
            renavam = v.renavam,
            chassi = v.chassi,
            comboio = v.comboio
        )
    }

    fun listarTodos(): List<VeiculoResponse> {
        val tipos = tiposPorId()
        return veiculoRepository.findAllAtivos().map { toResponse(it, tipos) }
    }

    fun listarDisponiveis(): List<VeiculoResponse> {
        val tipos = tiposPorId()
        return veiculoRepository.findDisponiveis().map { toResponse(it, tipos) }
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
            .orElseThrow { NaoEncontradoException("Veículo não encontrado.") }
        return toResponse(veiculo, tiposPorId())
    }

    // Placa é opcional (máquinas não têm). Normalização (decisão 19/10): maiúscula, sem hífen
    // e sem espaço; vazio vira null (dois '' colidiriam no índice único veiculos_placa_key).
    private fun placaInformada(request: VeiculoRequest): String? =
        request.placa?.uppercase()?.replace("-", "")?.replace(" ", "")?.takeIf { it.isNotEmpty() }

    // O banco tem índice único na placa, inclusive de veículo inativo (inativo segura a placa).
    private fun conferirPlacaUnica(placa: String?, ignorarId: String?) {
        if (placa == null) return
        // Mesma normalização da consulta: só A-Z e 0-9.
        val normalizada = placa.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
        if (normalizada.isEmpty()) return
        val status = veiculoRepository.statusDosVeiculosComPlaca(normalizada, ignorarId)
        if (status.any { it != "inativo" })
            throw ConflitoException("Já existe um veículo com esta placa.", CodigosErro.PLACA_EM_USO)
        if (status.isNotEmpty())
            throw ConflitoException(
                "Já existe um veículo inativo com esta placa. Reative-o em vez de cadastrar novo.",
                CodigosErro.PLACA_DE_VEICULO_INATIVO
            )
    }
    fun cadastrar(request: VeiculoRequest) {
        val tipoId = request.tipoId?.takeIf { it.isNotBlank() }
            ?: throw RegraNegocioException("Campo 'tipoId' é obrigatório.")
        conferirPlacaUnica(placaInformada(request), null)
        veiculoRepository.inserirVeiculo(
            tipoId = tipoId,
            placa = placaInformada(request),
            descricao = request.descricao,
            medidorAtual = request.medidorAtual,
            marca = request.marca?.uppercase(),
            modelo = request.modelo?.uppercase(),
            ano = request.ano,
            cor = request.cor?.uppercase(),
            renavam = request.renavam,
            chassi = request.chassi?.uppercase(),
            comboio = request.comboio
        )
    }
    fun atualizar(id: String, request: VeiculoRequest) {
        veiculoRepository.findById(UUID.fromString(id))
            .orElseThrow { NaoEncontradoException("Veículo não encontrado.") }
        conferirPlacaUnica(placaInformada(request), id)
        veiculoRepository.atualizarVeiculo(
            id = id,
            placa = placaInformada(request),
            descricao = request.descricao,
            marca = request.marca?.uppercase(),
            modelo = request.modelo?.uppercase(),
            ano = request.ano,
            cor = request.cor?.uppercase(),
            renavam = request.renavam,
            chassi = request.chassi?.uppercase(),
            comboio = request.comboio
        )
    }

    // PUT /veiculos/{id}/ativo (rota nova). Reativar volta como disponivel, mas é
    // recusado (400) se outro veículo ativo já usa a mesma placa.
    fun alterarAtivo(id: String, ativo: Boolean) {
        val veiculo = veiculoRepository.findById(uuidValido(id))
            .orElseThrow { NaoEncontradoException("Veículo não encontrado.") }
        if (!ativo) return inativar(id)
        if (veiculo.status != "inativo") return
        val placa = veiculo.placa?.uppercase()?.filter { it in 'A'..'Z' || it in '0'..'9' }
        if (!placa.isNullOrEmpty() && veiculoRepository.contarPorPlaca(placa, id) > 0)
            throw RegraNegocioException("Já existe um veículo ativo com esta placa.")
        veiculoRepository.atualizarStatus(id, "disponivel")
    }

    fun inativar(id: String) {
        veiculoRepository.findById(UUID.fromString(id))
            .orElseThrow { NaoEncontradoException("Veículo não encontrado.") }
        veiculoRepository.inativarVeiculo(id)
    }

    fun listarComboios(): List<VeiculoResponse> {
        val tipos = tiposPorId()
        return veiculoRepository.findComboios().map { toResponse(it, tipos) }
    }

    fun buscarMotoristas(id: String): Map<String, Any?> {
        val maisUsou = veiculoRepository.findMotoristaMaisUsou(id).firstOrNull()
        val ultimo = veiculoRepository.findUltimoMotorista(id).firstOrNull()
        return mapOf(
            "maisUsou" to if (maisUsou != null) mapOf(
                "id" to maisUsou[0].toString(),
                "nomeCompleto" to maisUsou[1].toString(),
                "login" to maisUsou[2].toString(),
                "fotoPerfil" to maisUsou[3]?.toString(),
                "totalUsos" to maisUsou[4].toString().toInt()
            ) else null,
            "ultimo" to if (ultimo != null) mapOf(
                "id" to ultimo[0].toString(),
                "nomeCompleto" to ultimo[1].toString(),
                "login" to ultimo[2].toString(),
                "fotoPerfil" to ultimo[3]?.toString(),
                "ultimoUso" to ultimo[4].toString()
            ) else null
        )
    }
}