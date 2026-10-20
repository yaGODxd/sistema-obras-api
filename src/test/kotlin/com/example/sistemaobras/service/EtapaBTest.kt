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
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Parte 3, etapa B: transações, medidor final e texto do log do abastecimento.
class EtapaBTest {

    // Mockito.eq() devolve null; o Kotlin barraria null num parâmetro não nulo.
    private fun <T> igual(valor: T): T {
        Mockito.eq(valor)
        return valor
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

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
        val diarioDoEvento = mock(DiarioDoEvento::class.java)
        val id = UUID.randomUUID()
        given(diarioRepository.findDiarioAberto("m")).willReturn(DiarioBordo(id = id, medidorInicial = BigDecimal("1000")))
        given(diarioDoEvento.porId(id.toString())).willReturn(DiarioDoEvento.Diario(
            id, "m", "aberto", LocalDateTime.now(ZoneOffset.UTC).minusHours(1), null, UUID.randomUUID(), null, null, 1000.0
        ))
        val service = DiarioBordoService(
            diarioRepository, mock(TurnoRepository::class.java), mock(VeiculoRepository::class.java), mock(LogService::class.java),
            mock(AssinaturaRepository::class.java), diarioDoEvento
        )

        val erro = assertFailsWith<RegraNegocioException> { service.fecharDiario("m", FecharDiarioRequest(medidorFinal = 999.0)) }
        assertEquals("O medidor final não pode ser menor que o medidor inicial.", erro.message)
        verify(diarioRepository, never()).fecharDiario(
            anyString(), anyDouble(), qualquer(), qualquer(), qualquer(), qualquer(), qualquer(), Mockito.anyBoolean(), qualquer()
        )
    }

    @Test
    fun `log do abastecimento no comboio com posto mantem o sufixo Comboio`() {
        val diarioRepository = mock(DiarioBordoRepository::class.java)
        val logService = mock(LogService::class.java)
        val diarioDoEvento = mock(DiarioDoEvento::class.java)
        val veiculoAbastecido = UUID.randomUUID()
        // Comboio: o diário é o do veículo abastecido no horário do evento.
        given(diarioDoEvento.porVeiculoNoHorario(igual(veiculoAbastecido.toString()), qualquer())).willReturn(DiarioDoEvento.Diario(
            UUID.randomUUID(), "outro", "aberto", LocalDateTime.now(ZoneOffset.UTC).minusHours(1), null, veiculoAbastecido, null, null, 0.0
        ))
        val service = AbastecimentoService(
            mock(AbastecimentoRepository::class.java), diarioRepository, mock(UsuarioRepository::class.java), logService, diarioDoEvento
        )

        service.registrar(AbastecimentoRequest(
            loginMotorista = "m", tipoCombustivel = "diesel", litros = 10.0, valorTotal = 0.0,
            posto = "Comboio 1", veiculoAbastecidoId = veiculoAbastecido.toString()
        ))

        val litros = "%.1f".format(10.0)
        val valor = "%.2f".format(0.0)
        verify(logService).registrar(
            "m", null, "ABASTECIMENTO",
            "Abastecimento registrado — diesel, $litros L, R$ $valor, Posto: Comboio 1 (Comboio)", null
        )
    }
}
