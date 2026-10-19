package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.FecharDiarioRequest
import com.example.sistemaobras.entity.DiarioBordo
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.repository.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Parte 3, etapa B: transações, medidor final e texto do log do abastecimento.
class EtapaBTest {

    @Test
    fun `fluxos de escrita com varios passos rodam em transacao`() {
        fun temTransacao(classe: KClass<*>, metodo: String) =
            classe.java.declaredMethods.filter { it.name == metodo }.any { it.isAnnotationPresent(Transactional::class.java) }

        assertTrue(temTransacao(DiarioBordoService::class, "abrirDiario"))
        assertTrue(temTransacao(DiarioBordoService::class, "fecharDiario"))
        assertTrue(temTransacao(AbastecimentoService::class, "registrar"))
        assertTrue(temTransacao(OcorrenciaService::class, "registrar"))
        assertTrue(temTransacao(OcorrenciaService::class, "atualizarStatus"))
        assertTrue(temTransacao(ManutencaoService::class, "abrir"))
        assertTrue(temTransacao(ManutencaoService::class, "atualizarStatus"))
    }

    @Test
    fun `medidor final menor que o inicial e recusado sem gravar`() {
        val diarioRepository = mock(DiarioBordoRepository::class.java)
        given(diarioRepository.findDiarioAberto("m")).willReturn(
            DiarioBordo(id = UUID.randomUUID(), veiculoId = UUID.randomUUID(), medidorInicial = BigDecimal("1000"))
        )
        val service = DiarioBordoService(
            diarioRepository, mock(TurnoRepository::class.java), mock(VeiculoRepository::class.java), mock(LogService::class.java),
            mock(AssinaturaRepository::class.java)
        )

        val erro = assertFailsWith<RegraNegocioException> { service.fecharDiario("m", FecharDiarioRequest(medidorFinal = 999.0)) }
        assertEquals("O medidor final não pode ser menor que o medidor inicial.", erro.message)
        verify(diarioRepository, never()).fecharDiario(anyString(), anyDouble(), Mockito.any())
    }

    @Test
    fun `log do abastecimento no comboio com posto mantem o sufixo Comboio`() {
        val diarioRepository = mock(DiarioBordoRepository::class.java)
        val logService = mock(LogService::class.java)
        val veiculoAbastecido = UUID.randomUUID().toString()
        given(diarioRepository.temDiarioAbertoPorVeiculo(veiculoAbastecido)).willReturn(1L)
        val service = AbastecimentoService(
            mock(AbastecimentoRepository::class.java), diarioRepository, mock(UsuarioRepository::class.java), logService
        )

        service.registrar(AbastecimentoRequest(
            loginMotorista = "m", tipoCombustivel = "diesel", litros = 10.0, valorTotal = 0.0,
            posto = "Comboio 1", veiculoAbastecidoId = veiculoAbastecido
        ))

        val litros = "%.1f".format(10.0)
        val valor = "%.2f".format(0.0)
        verify(logService).registrar(
            "m", null, "ABASTECIMENTO",
            "Abastecimento registrado — diesel, $litros L, R$ $valor, Posto: Comboio 1 (Comboio)", null
        )
    }
}
