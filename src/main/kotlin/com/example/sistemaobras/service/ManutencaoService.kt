package com.example.sistemaobras.service

import com.example.sistemaobras.dto.*
import com.example.sistemaobras.entity.Manutencao
import com.example.sistemaobras.entity.ManutencaoPeca
import com.example.sistemaobras.repository.ManutencaoPecaRepository
import com.example.sistemaobras.repository.ManutencaoRepository
import com.example.sistemaobras.repository.VeiculoRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Service
class ManutencaoService(
    private val manutencaoRepository: ManutencaoRepository,
    private val manutencaoPecaRepository: ManutencaoPecaRepository,
    private val veiculoRepository: VeiculoRepository
) {

    fun abrir(request: ManutencaoRequest): ManutencaoResponse {
        val veiculo = veiculoRepository.findById(UUID.fromString(request.veiculoId))
            .orElseThrow { RuntimeException("Veículo não encontrado") }

        val manutencao = manutencaoRepository.save(Manutencao(
            veiculoId = veiculo.id,
            ocorrenciaId = request.ocorrenciaId?.let { UUID.fromString(it) },
            tipo = request.tipo,
            descricao = request.descricao,
            mecanicoResponsavel = request.mecanicoResponsavel,
            oficina = request.oficina,
            custoMaoObra = BigDecimal.valueOf(request.custoMaoObra),
            observacoes = request.observacoes
        ))

        val pecas = request.pecas.map { p ->
            manutencaoPecaRepository.save(ManutencaoPeca(
                manutencaoId = manutencao.id,
                nomePeca = p.nomePeca,
                codigoPeca = p.codigoPeca,
                marcaPeca = p.marcaPeca,
                quantidade = p.quantidade,
                valorUnitario = BigDecimal.valueOf(p.valorUnitario)
            ))
        }

        // Muda status do veículo para em_manutencao
        veiculoRepository.atualizarStatus(request.veiculoId, "em_manutencao")

        return toResponse(manutencao, pecas, veiculo.descricao, veiculo.placa)
    }

    fun atualizarStatus(id: String, request: AtualizarStatusRequest): ManutencaoResponse {
        val manutencao = manutencaoRepository.findById(UUID.fromString(id))
            .orElseThrow { RuntimeException("Manutenção não encontrada") }

        val atualizada = manutencao.copy(
            status = request.status,
            observacoes = request.observacoes ?: manutencao.observacoes,
            concluidaEm = if (request.status == "concluida") LocalDateTime.now() else manutencao.concluidaEm,
            atualizadoEm = LocalDateTime.now()
        )

        manutencaoRepository.save(atualizada)

        // Se concluída volta veículo para disponível
        if (request.status == "concluida") {
            veiculoRepository.atualizarStatus(manutencao.veiculoId.toString(), "disponivel")
        }

        val pecas = manutencaoPecaRepository.findByManutencaoId(manutencao.id!!)
        val veiculo = veiculoRepository.findById(manutencao.veiculoId!!).orElseThrow()
        return toResponse(atualizada, pecas, veiculo.descricao, veiculo.placa)
    }

    fun listarTodas(): List<ManutencaoResponse> {
        return manutencaoRepository.findAll()
            .sortedByDescending { it.abertaEm }
            .map { toResponseSimples(it) }
    }

    fun listarAbertas(): List<ManutencaoResponse> {
        return manutencaoRepository.findAbertas()
            .map { toResponseSimples(it) }
    }

    fun listarPorVeiculo(veiculoId: String): List<ManutencaoResponse> {
        return manutencaoRepository.findByVeiculoId(UUID.fromString(veiculoId))
            .sortedByDescending { it.abertaEm }
            .map { toResponseSimples(it) }
    }

    fun buscarPorId(id: String): ManutencaoResponse {
        val manutencao = manutencaoRepository.findById(UUID.fromString(id))
            .orElseThrow { RuntimeException("Manutenção não encontrada") }
        val pecas = manutencaoPecaRepository.findByManutencaoId(manutencao.id!!)
        val veiculo = veiculoRepository.findById(manutencao.veiculoId!!).orElseThrow()
        return toResponse(manutencao, pecas, veiculo.descricao, veiculo.placa)
    }

    fun adicionarPeca(manutencaoId: String, request: PecaRequest): PecaResponse {
        val peca = manutencaoPecaRepository.save(ManutencaoPeca(
            manutencaoId = UUID.fromString(manutencaoId),
            nomePeca = request.nomePeca,
            codigoPeca = request.codigoPeca,
            marcaPeca = request.marcaPeca,
            quantidade = request.quantidade,
            valorUnitario = BigDecimal.valueOf(request.valorUnitario)
        ))
        return toPecaResponse(peca)
    }

    fun removerPeca(pecaId: String) {
        manutencaoPecaRepository.deleteById(UUID.fromString(pecaId))
    }

    private fun toResponseSimples(m: Manutencao): ManutencaoResponse {
        val pecas = manutencaoPecaRepository.findByManutencaoId(m.id!!)
        val veiculo = veiculoRepository.findById(m.veiculoId!!).orElseThrow()
        return toResponse(m, pecas, veiculo.descricao, veiculo.placa)
    }

    private fun toResponse(
        m: Manutencao,
        pecas: List<ManutencaoPeca>,
        veiculoDescricao: String,
        veiculoPlaca: String?
    ): ManutencaoResponse {
        val custoPecas = pecas.sumOf { it.quantidade * it.valorUnitario.toDouble() }
        return ManutencaoResponse(
            id = m.id,
            veiculoId = m.veiculoId,
            veiculoDescricao = veiculoDescricao,
            veiculoPlaca = veiculoPlaca,
            ocorrenciaId = m.ocorrenciaId,
            tipo = m.tipo,
            descricao = m.descricao,
            mecanicoResponsavel = m.mecanicoResponsavel,
            oficina = m.oficina,
            custoMaoObra = m.custoMaoObra.toDouble(),
            custoPecas = custoPecas,
            custoTotal = m.custoMaoObra.toDouble() + custoPecas,
            status = m.status,
            abertaEm = m.abertaEm,
            concluidaEm = m.concluidaEm,
            observacoes = m.observacoes,
            pecas = pecas.map { toPecaResponse(it) }
        )
    }

    private fun toPecaResponse(p: ManutencaoPeca) = PecaResponse(
        id = p.id,
        nomePeca = p.nomePeca,
        codigoPeca = p.codigoPeca,
        marcaPeca = p.marcaPeca,
        quantidade = p.quantidade,
        valorUnitario = p.valorUnitario.toDouble(),
        valorTotal = p.quantidade * p.valorUnitario.toDouble()
    )
}