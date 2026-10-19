package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.OcorrenciaRequest
import com.example.sistemaobras.entity.DiarioBordo
import com.example.sistemaobras.entity.Ocorrencia
import com.example.sistemaobras.repository.*
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

// Parte 6A: o registradoEm (epoch em ms) é um instante absoluto e vira hora UTC.
class HorarioUtcTest {

    // 19/10/2026 12:00 em UTC (09:00 em Brasília).
    private val instante = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
    private val esperadoUtc = LocalDateTime.of(2026, 10, 19, 12, 0)

    private val diarioRepository = mock(DiarioBordoRepository::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

    @Test
    fun `abastecimento grava o registradoEm em UTC`() {
        given(diarioRepository.temDiarioAberto("m")).willReturn(1L)
        val service = AbastecimentoService(
            mock(AbastecimentoRepository::class.java), diarioRepository,
            mock(UsuarioRepository::class.java), mock(LogService::class.java)
        )
        val resposta = service.registrar(
            AbastecimentoRequest(loginMotorista = "m", tipoCombustivel = "diesel", litros = 10.0, valorTotal = 60.0, registradoEm = instante)
        )
        assertEquals(esperadoUtc, resposta.registradoEm)
    }

    @Test
    fun `ocorrencia grava o registradoEm em UTC`() {
        given(diarioRepository.findDiarioAberto("m")).willReturn(DiarioBordo(id = UUID.randomUUID()))
        val ocorrenciaRepository = mock(OcorrenciaRepository::class.java)
        given(ocorrenciaRepository.save(qualquer<Ocorrencia>())).willAnswer { it.arguments[0] }
        val service = OcorrenciaService(
            ocorrenciaRepository, diarioRepository, mock(UsuarioRepository::class.java),
            mock(VeiculoRepository::class.java), mock(ManutencaoRepository::class.java), mock(LogService::class.java)
        )
        val resposta = service.registrar(
            OcorrenciaRequest(loginMotorista = "m", tipo = "pneu", descricao = "furado", registradoEm = instante)
        )
        assertEquals(esperadoUtc, resposta.registradoEm)
    }
}
