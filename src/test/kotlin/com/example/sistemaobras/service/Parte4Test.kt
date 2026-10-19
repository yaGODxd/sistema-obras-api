package com.example.sistemaobras.service

import com.example.sistemaobras.dto.VeiculoRequest
import com.example.sistemaobras.entity.Abastecimento
import com.example.sistemaobras.entity.Log
import com.example.sistemaobras.entity.Manutencao
import com.example.sistemaobras.entity.ManutencaoPeca
import com.example.sistemaobras.entity.Veiculo
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.repository.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Parte 4: respostas mais completas e listas sem consulta repetida por linha.
class Parte4Test {

    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

    // ---------- Veículo ----------

    private val tipoId = UUID.randomUUID()

    @Test
    fun `lista de veiculos traz tipo real com uma consulta so para os tipos`() {
        val repo = mock(VeiculoRepository::class.java)
        given(repo.listarTipos()).willReturn(listOf(arrayOf<Any?>(tipoId.toString(), "Caminhão", "km")))
        given(repo.findAllAtivos()).willReturn(List(5) { Veiculo(id = UUID.randomUUID(), tipoId = tipoId, descricao = "V$it") })

        val lista = VeiculoService(repo).listarTodos()

        assertEquals(5, lista.size)
        assertTrue(lista.all { it.tipoId == tipoId && it.tipoNome == "Caminhão" && it.tipoMedidor == "km" })
        verify(repo, times(1)).listarTipos()
    }

    @Test
    fun `cadastro de veiculo exige tipoId mas a edicao nao`() {
        val repo = mock(VeiculoRepository::class.java)
        val service = VeiculoService(repo)
        val erro = assertFailsWith<RegraNegocioException> { service.cadastrar(VeiculoRequest(placa = null, descricao = "Trator")) }
        assertEquals("Campo 'tipoId' é obrigatório.", erro.message)

        val id = UUID.randomUUID()
        given(repo.findById(id)).willReturn(java.util.Optional.of(Veiculo(id = id)))
        service.atualizar(id.toString(), VeiculoRequest(placa = null, descricao = "Trator"))   // sem tipoId: ok
    }

    // ---------- Logs: foto ----------

    @Test
    fun `tela de logs busca as fotos numa consulta so`() {
        val logRepository = mock(LogRepository::class.java)
        val usuarioRepository = mock(UsuarioRepository::class.java)
        given(logRepository.findAll()).willReturn(List(50) { Log(usuarioLogin = if (it % 2 == 0) "ana" else "beto", acao = "LOGIN") })
        given(usuarioRepository.findFotosByLogins(qualquer())).willReturn(listOf(arrayOf<Any?>("ana", "FOTO_ANA"), arrayOf<Any?>("beto", null)))

        val logs = LogService(logRepository, usuarioRepository, mock(PlatformTransactionManager::class.java)).listarTodos()

        assertEquals("FOTO_ANA", logs.first { it.usuarioLogin == "ana" }.usuarioFoto)
        assertNull(logs.first { it.usuarioLogin == "beto" }.usuarioFoto)
        verify(usuarioRepository, times(1)).findFotosByLogins(qualquer())
        verify(usuarioRepository, never()).findFotoByLogin(anyString())
    }

    // ---------- Manutenções: peças e veículo ----------

    @Test
    fun `lista de manutencoes busca pecas e veiculos em lote`() {
        val manutencaoRepository = mock(ManutencaoRepository::class.java)
        val pecaRepository = mock(ManutencaoPecaRepository::class.java)
        val veiculoRepository = mock(VeiculoRepository::class.java)
        val veiculo = Veiculo(id = UUID.randomUUID(), descricao = "Caminhão", placa = "ABC1D23")
        val manutencoes = List(10) { Manutencao(id = UUID.randomUUID(), veiculoId = veiculo.id, tipo = "corretiva", descricao = "M$it") }
        given(manutencaoRepository.findAll()).willReturn(manutencoes)
        given(pecaRepository.findByManutencaoIdIn(qualquer())).willReturn(
            listOf(ManutencaoPeca(manutencaoId = manutencoes[0].id, nomePeca = "Pastilha", quantidade = 2, valorUnitario = BigDecimal("50")))
        )
        given(veiculoRepository.findAllById(qualquer())).willReturn(listOf(veiculo))

        val service = ManutencaoService(
            manutencaoRepository, pecaRepository, veiculoRepository, mock(DiarioBordoRepository::class.java),
            mock(OcorrenciaService::class.java), mock(LogService::class.java)
        )
        val lista = service.listarTodas()

        assertEquals(10, lista.size)
        assertEquals("Caminhão", lista[0].veiculoDescricao)
        assertEquals(100.0, lista.first { it.id == manutencoes[0].id }.custoPecas)
        verify(pecaRepository, times(1)).findByManutencaoIdIn(qualquer())
        verify(pecaRepository, never()).findByManutencaoId(qualquer())
        verify(veiculoRepository, times(1)).findAllById(qualquer())
        verify(veiculoRepository, never()).findById(qualquer())
    }

    // ---------- Diário: detalhe ----------

    private fun diarioService(repo: DiarioBordoRepository) = DiarioBordoService(
        repo, mock(TurnoRepository::class.java), mock(VeiculoRepository::class.java), mock(LogService::class.java)
    )

    @Test
    fun `detalhe do diario traz motivo, observacao, assinatura, veiculo e medidor`() {
        val repo = mock(DiarioBordoRepository::class.java)
        val id = UUID.randomUUID()
        val veiculoId = UUID.randomUUID()
        val motivoId = UUID.randomUUID()
        given(repo.buscarDetalhe(id.toString())).willReturn(listOf(arrayOf<Any?>(
            id.toString(), "Caminhão", "ABC1D23", "Ana", "1000", "1100", "100", "Centro", "assinado",
            "2026-10-19 12:00:00", "2026-10-19 18:00:00", veiculoId.toString(), "km",
            motivoId.toString(), "Entrega", "Sem problemas", "2026-10-19 18:05:00", "ana"
        )))

        val d = diarioService(repo).buscarDetalhe(id.toString())

        assertEquals(veiculoId, d.veiculoId)
        assertEquals("km", d.tipoMedidor)
        assertEquals("Entrega", d.motivoUso)
        assertEquals(motivoId.toString(), d.motivoUsoId)
        assertEquals("Sem problemas", d.observacaoFechamento)
        assertEquals("ana", d.motoristaLogin)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 19, 18, 5), d.assinadoEm)
    }

    @Test
    fun `detalhe do diario com id invalido ou inexistente`() {
        val repo = mock(DiarioBordoRepository::class.java)
        assertFailsWith<RegraNegocioException> { diarioService(repo).buscarDetalhe("abc") }
        assertFailsWith<NaoEncontradoException> { diarioService(repo).buscarDetalhe(UUID.randomUUID().toString()) }
    }

    // ---------- Abastecimento: comboio ----------

    @Test
    fun `abastecimento informa se foi no comboio`() {
        val repo = mock(AbastecimentoRepository::class.java)
        val outro = UUID.randomUUID()
        given(repo.findByDiarioId("d")).willReturn(listOf(
            Abastecimento(tipoCombustivel = "diesel", litros = BigDecimal.TEN),
            Abastecimento(tipoCombustivel = "diesel", litros = BigDecimal.TEN, veiculoAbastecidoId = outro)
        ))
        val lista = AbastecimentoService(repo, mock(DiarioBordoRepository::class.java), mock(UsuarioRepository::class.java), mock(LogService::class.java))
            .listarPorDiario("d")
        assertFalse(lista[0].comboio)
        assertTrue(lista[1].comboio)
        assertEquals(outro, lista[1].veiculoAbastecidoId)
    }
}
