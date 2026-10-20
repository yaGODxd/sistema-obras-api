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
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

// Parte 6A: o registradoEm (epoch em ms) é um instante absoluto e vira hora UTC.
class HorarioUtcTest {

    // Uma hora atrás, em UTC (o epoch é um instante absoluto; o banco guarda UTC).
    private val esperadoUtc = LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(1)
    private val instante = esperadoUtc.toInstant(ZoneOffset.UTC).toEpochMilli()

    private val diarioRepository = mock(DiarioBordoRepository::class.java)
    private val diarioDoEvento = mock(DiarioDoEvento::class.java)
    private val diarioId = UUID.randomUUID()

    // Diário aberto há 3 h, do motorista "m".
    private fun diarioAberto() {
        given(diarioRepository.findDiarioAberto("m")).willReturn(DiarioBordo(id = diarioId))
        given(diarioDoEvento.porId(diarioId.toString())).willReturn(DiarioDoEvento.Diario(
            diarioId, "m", "aberto", esperadoUtc.minusHours(2), null, UUID.randomUUID(), null, null, 0.0
        ))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

    @Test
    fun `abastecimento grava o registradoEm em UTC`() {
        diarioAberto()
        val service = AbastecimentoService(
            mock(AbastecimentoRepository::class.java), diarioRepository,
            mock(UsuarioRepository::class.java), mock(LogService::class.java), diarioDoEvento
        )
        val resposta = service.registrar(
            AbastecimentoRequest(loginMotorista = "m", tipoCombustivel = "diesel", litros = 10.0, valorTotal = 60.0, registradoEm = instante)
        )
        assertEquals(esperadoUtc, resposta.registradoEm)
    }

    @Test
    fun `ocorrencia grava o registradoEm em UTC`() {
        diarioAberto()
        val ocorrenciaRepository = mock(OcorrenciaRepository::class.java)
        given(ocorrenciaRepository.save(qualquer<Ocorrencia>())).willAnswer { it.arguments[0] }
        val service = OcorrenciaService(
            ocorrenciaRepository, diarioRepository, mock(UsuarioRepository::class.java),
            mock(VeiculoRepository::class.java), mock(ManutencaoRepository::class.java), mock(LogService::class.java),
            diarioDoEvento
        )
        val resposta = service.registrar(
            OcorrenciaRequest(loginMotorista = "m", tipo = "pneu", descricao = "furado", registradoEm = instante)
        )
        assertEquals(esperadoUtc, resposta.registradoEm)
    }
}
