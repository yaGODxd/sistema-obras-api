package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AtualizarStatusRequest
import com.example.sistemaobras.dto.ManutencaoRequest
import com.example.sistemaobras.entity.Manutencao
import com.example.sistemaobras.entity.Ocorrencia
import com.example.sistemaobras.entity.Veiculo
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.repository.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Parte 3 (itens 18 e 19): status da ocorrência pelas manutenções e status do veículo.
class ManutencaoOcorrenciaTest {

    private val manutencaoRepository = mock(ManutencaoRepository::class.java)
    private val ocorrenciaRepository = mock(OcorrenciaRepository::class.java)
    private val veiculoRepository = mock(VeiculoRepository::class.java)
    private val diarioRepository = mock(DiarioBordoRepository::class.java)
    private val logService = mock(LogService::class.java)

    private val ocorrenciaService = OcorrenciaService(
        ocorrenciaRepository, diarioRepository, mock(UsuarioRepository::class.java),
        veiculoRepository, manutencaoRepository, logService
    )
    private val service = ManutencaoService(
        manutencaoRepository, mock(ManutencaoPecaRepository::class.java), veiculoRepository,
        diarioRepository, ocorrenciaService, logService
    )

    private val veiculoId = UUID.randomUUID()
    private val ocorrenciaId = UUID.randomUUID()
    private val m1 = UUID.randomUUID()
    private val m2 = UUID.randomUUID()

    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

    @BeforeEach
    fun preparar() {
        given(manutencaoRepository.save(qualquer<Manutencao>())).willAnswer { it.arguments[0] }
        given(ocorrenciaRepository.save(qualquer<Ocorrencia>())).willAnswer { it.arguments[0] }
        given(veiculoRepository.findById(veiculoId)).willReturn(Optional.of(Veiculo(id = veiculoId, descricao = "Caminhão")))
    }

    private fun manutencao(id: UUID, status: String, ocorrencia: UUID? = ocorrenciaId) =
        Manutencao(id = id, veiculoId = veiculoId, ocorrenciaId = ocorrencia, tipo = "corretiva", descricao = "Freio", status = status)

    private fun ocorrenciaCom(status: String) {
        given(ocorrenciaRepository.findById(ocorrenciaId)).willReturn(Optional.of(
            Ocorrencia(id = ocorrenciaId, diarioId = UUID.randomUUID(), tipo = "freio", descricao = "barulho", status = status,
                resolvidaEm = if (status == "concluida") LocalDateTime.now() else null)
        ))
    }

    // Banco: manutenções existentes (antes da mudança).
    private fun existentes(vararg lista: Manutencao) {
        lista.forEach { given(manutencaoRepository.findById(it.id!!)).willReturn(Optional.of(it)) }
        given(manutencaoRepository.findByOcorrenciaId(ocorrenciaId)).willReturn(lista.filter { it.ocorrenciaId == ocorrenciaId })
        given(manutencaoRepository.findByVeiculoId(veiculoId)).willReturn(lista.toList())
    }

    private fun ocorrenciaGravada(): Ocorrencia {
        val captor = ArgumentCaptor.forClass(Ocorrencia::class.java)
        verify(ocorrenciaRepository, Mockito.atLeastOnce()).save(captor.capture())
        return captor.value
    }

    private fun concluir(id: UUID) = service.atualizarStatus(id.toString(), AtualizarStatusRequest(status = "concluida"))

    @Test
    fun `uma manutencao concluida conclui a ocorrencia e libera o veiculo`() {
        ocorrenciaCom("em_andamento")
        existentes(manutencao(m1, "em_andamento"))

        concluir(m1)

        val ocorrencia = ocorrenciaGravada()
        assertEquals("concluida", ocorrencia.status)
        assertNotNull(ocorrencia.resolvidaEm)
        verify(veiculoRepository).atualizarStatus(veiculoId.toString(), "disponivel")
    }

    @Test
    fun `com duas vinculadas so conclui quando as duas concluirem`() {
        ocorrenciaCom("em_andamento")
        existentes(manutencao(m1, "em_andamento"), manutencao(m2, "aberta"))

        concluir(m1)
        verify(ocorrenciaRepository, never()).save(qualquer<Ocorrencia>())   // continua em_andamento
        verify(veiculoRepository).atualizarStatus(veiculoId.toString(), "em_manutencao") // a outra segue aberta

        existentes(manutencao(m1, "concluida"), manutencao(m2, "em_andamento"))
        concluir(m2)
        assertEquals("concluida", ocorrenciaGravada().status)
    }

    @Test
    fun `reabrir a manutencao volta a ocorrencia para em_andamento`() {
        ocorrenciaCom("concluida")
        existentes(manutencao(m1, "concluida"))

        service.atualizarStatus(m1.toString(), AtualizarStatusRequest(status = "aberta"))

        val ocorrencia = ocorrenciaGravada()
        assertEquals("em_andamento", ocorrencia.status)
        assertNull(ocorrencia.resolvidaEm)
        verify(veiculoRepository).atualizarStatus(veiculoId.toString(), "em_manutencao")
    }

    @Test
    fun `veiculo com outra manutencao aberta nao volta a ficar disponivel`() {
        existentes(manutencao(m1, "em_andamento", null), manutencao(m2, "aberta", null))
        concluir(m1)
        verify(veiculoRepository).atualizarStatus(veiculoId.toString(), "em_manutencao")
        verify(veiculoRepository, never()).atualizarStatus(veiculoId.toString(), "disponivel")
    }

    @Test
    fun `veiculo com diario aberto volta para em_uso`() {
        existentes(manutencao(m1, "em_andamento", null))
        given(diarioRepository.temDiarioAbertoPorVeiculo(veiculoId.toString())).willReturn(1L)
        concluir(m1)
        verify(veiculoRepository).atualizarStatus(veiculoId.toString(), "em_uso")
    }

    @Test
    fun `abrir manutencao vinculada deixa a ocorrencia em_andamento`() {
        ocorrenciaCom("aberta")
        existentes()
        service.abrir(ManutencaoRequest(veiculoId = veiculoId.toString(), ocorrenciaId = ocorrenciaId.toString(), tipo = "corretiva", descricao = "Freio"))
        assertEquals("em_andamento", ocorrenciaGravada().status)
    }

    @Test
    fun `abrir manutencao com ocorrencia inexistente nao grava nada`() {
        given(ocorrenciaRepository.findById(ocorrenciaId)).willReturn(Optional.empty())
        assertFailsWith<NaoEncontradoException> {
            service.abrir(ManutencaoRequest(veiculoId = veiculoId.toString(), ocorrenciaId = ocorrenciaId.toString(), tipo = "corretiva", descricao = "Freio"))
        }
        verify(manutencaoRepository, never()).save(qualquer<Manutencao>())
    }

    @Test
    fun `gestor descarta a ocorrencia a mao e fica registrado no log`() {
        ocorrenciaCom("aberta")
        val resposta = ocorrenciaService.atualizarStatus(ocorrenciaId.toString(), "descartada")
        assertEquals("descartada", resposta.status)
        assertNotNull(resposta.resolvidaEm)
        verify(logService).registrar(
            "sistema", null, "OCORRENCIA_STATUS",
            "Ocorrência $ocorrenciaId passou de 'aberta' para 'descartada' manualmente", null
        )
    }
}
