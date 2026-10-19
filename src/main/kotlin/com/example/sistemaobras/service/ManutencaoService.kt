package com.example.sistemaobras.service

import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.uuidValido
import com.example.sistemaobras.dto.*
import com.example.sistemaobras.entity.Manutencao
import com.example.sistemaobras.entity.ManutencaoPeca
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.ManutencaoPecaRepository
import com.example.sistemaobras.repository.ManutencaoRepository
import com.example.sistemaobras.repository.VeiculoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Service
class ManutencaoService(
    private val manutencaoRepository: ManutencaoRepository,
    private val manutencaoPecaRepository: ManutencaoPecaRepository,
    private val veiculoRepository: VeiculoRepository,
    private val diarioBordoRepository: DiarioBordoRepository,
    private val ocorrenciaService: OcorrenciaService,
    private val logService: LogService
) {

    // Login de quem fez a ação (token válido) ou "sistema" quando não há token.
    private fun autorDoLog(): String = logService.loginAutenticado() ?: "sistema"
    private fun nomeDoAutor(login: String): String? = if (login == "sistema") "Sistema" else null

    // Manutenção, peças, veículo, ocorrência e log: tudo ou nada (o log tem transação própria).
    @Transactional
    fun abrir(request: ManutencaoRequest): ManutencaoResponse {
        val veiculo = veiculoRepository.findById(UUID.fromString(request.veiculoId))
            .orElseThrow { NaoEncontradoException("Veículo não encontrado.") }
        request.ocorrenciaId?.let { ocorrenciaService.buscar(UUID.fromString(it)) }

        val manutencao = manutencaoRepository.save(Manutencao(
            veiculoId = veiculo.id,
            ocorrenciaId = request.ocorrenciaId?.let { UUID.fromString(it) },
            tipo = request.tipo,
            descricao = request.descricao,
            mecanicoResponsavel = request.mecanicoResponsavel,
            oficina = request.oficina,
            custoMaoObra = BigDecimal.valueOf(request.custoMaoObra),
            observacoes = request.observacoes,
            fotoNotaFiscal = request.fotoNotaFiscal
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

        veiculoRepository.atualizarStatus(request.veiculoId, "em_manutencao")
        ocorrenciaService.atualizarPelaManutencao(manutencao)

        val autor = autorDoLog()
        logService.registrar(
            usuarioLogin = autor,
            usuarioNome = nomeDoAutor(autor),
            acao = "MANUTENCAO_ABERTA",
            descricao = "Manutenção aberta — Veículo: ${veiculo.descricao}, " +
                    "Tipo: ${request.tipo}, Descrição: ${request.descricao}"
        )

        return toResponse(manutencao, pecas, veiculo.descricao, veiculo.placa)
    }

    // O status já chega validado (aberta, em_andamento ou concluida) pelo AtualizarStatusRequest.
    @Transactional
    fun atualizarStatus(id: String, request: AtualizarStatusRequest): ManutencaoResponse {
        val manutencao = manutencaoRepository.findById(UUID.fromString(id))
            .orElseThrow { NaoEncontradoException("Manutenção não encontrada.") }

        val atualizada = manutencaoRepository.save(manutencao.copy(
            status = request.status,
            observacoes = request.observacoes ?: manutencao.observacoes,
            concluidaEm = if (request.status == "concluida") LocalDateTime.now(java.time.ZoneOffset.UTC) else manutencao.concluidaEm,
            atualizadoEm = LocalDateTime.now(java.time.ZoneOffset.UTC)
        ))

        veiculoRepository.atualizarStatus(manutencao.veiculoId.toString(), statusDoVeiculo(atualizada))
        ocorrenciaService.atualizarPelaManutencao(atualizada)

        val pecas = manutencaoPecaRepository.findByManutencaoId(manutencao.id!!)
        val veiculo = veiculoRepository.findById(manutencao.veiculoId!!).orElseThrow()

        val autor = autorDoLog()
        logService.registrar(
            usuarioLogin = autor,
            usuarioNome = nomeDoAutor(autor),
            acao = "MANUTENCAO_STATUS",
            descricao = "Status da manutenção atualizado para '${request.status}' — Veículo: ${veiculo.descricao}"
        )

        return toResponse(atualizada, pecas, veiculo.descricao, veiculo.placa)
    }

    // Item 19: ao concluir, o veículo só volta a ficar livre se não houver outra
    // manutenção dele em aberto; se ele tem diário aberto, fica em_uso.
    private fun statusDoVeiculo(atual: Manutencao): String {
        if (atual.status != "concluida") return "em_manutencao"
        val veiculoId = atual.veiculoId!!
        val outraAberta = manutencaoRepository.findByVeiculoId(veiculoId)
            .any { it.id != atual.id && it.status != "concluida" }
        return when {
            outraAberta -> "em_manutencao"
            diarioBordoRepository.temDiarioAbertoPorVeiculo(veiculoId.toString()) > 0 -> "em_uso"
            else -> "disponivel"
        }
    }

    fun listarTodas(): List<ManutencaoResponse> {
        return emLote(manutencaoRepository.findAll().sortedByDescending { it.abertaEm })
    }

    fun listarAbertas(): List<ManutencaoResponse> {
        return emLote(manutencaoRepository.findAbertas())
    }

    fun listarPorVeiculo(veiculoId: String): List<ManutencaoResponse> {
        return emLote(manutencaoRepository.findByVeiculoId(UUID.fromString(veiculoId)).sortedByDescending { it.abertaEm })
    }

    // Peças e veículos de todas as manutenções da lista em 2 consultas
    // (antes eram 2 por manutenção).
    private fun emLote(manutencoes: List<Manutencao>): List<ManutencaoResponse> {
        if (manutencoes.isEmpty()) return emptyList()
        val pecas = manutencaoPecaRepository.findByManutencaoIdIn(manutencoes.mapNotNull { it.id }).groupBy { it.manutencaoId }
        val veiculos = veiculoRepository.findAllById(manutencoes.mapNotNull { it.veiculoId }.distinct()).associateBy { it.id }
        return manutencoes.map { m ->
            val veiculo = veiculos[m.veiculoId]
            toResponse(m, pecas[m.id] ?: emptyList(), veiculo?.descricao ?: "", veiculo?.placa)
        }
    }

    fun buscarPorId(id: String): ManutencaoResponse {
        val manutencao = manutencaoRepository.findById(UUID.fromString(id))
            .orElseThrow { NaoEncontradoException("Manutenção não encontrada.") }
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

    // PUT /manutencoes/pecas/{pecaId} (rota nova): 400 dado/id inválido, 404 peça não
    // existe, 409 manutenção já concluída.
    @Transactional
    fun atualizarPeca(pecaId: String, request: PecaRequest): PecaResponse {
        val peca = manutencaoPecaRepository.findById(uuidValido(pecaId))
            .orElseThrow { NaoEncontradoException("Peça não encontrada.") }
        val manutencao = manutencaoRepository.findById(peca.manutencaoId!!).orElse(null)
        if (manutencao?.status == "concluida")
            throw ConflitoException("Não é possível alterar peça de manutenção concluída.")
        val atualizada = manutencaoPecaRepository.save(peca.copy(
            nomePeca = request.nomePeca,
            codigoPeca = request.codigoPeca,
            marcaPeca = request.marcaPeca,
            quantidade = request.quantidade,
            valorUnitario = BigDecimal.valueOf(request.valorUnitario)
        ))
        return toPecaResponse(atualizada)
    }

    fun removerPeca(pecaId: String) {
        manutencaoPecaRepository.deleteById(UUID.fromString(pecaId))
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
            fotoNotaFiscal = m.fotoNotaFiscal,
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