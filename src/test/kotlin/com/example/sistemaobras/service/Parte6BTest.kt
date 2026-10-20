package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.FecharDiarioRequest
import com.example.sistemaobras.dto.OcorrenciaRequest
import com.example.sistemaobras.dto.PontoLoteRequest
import com.example.sistemaobras.dto.RastreamentoLoteRequest
import com.example.sistemaobras.dto.TurnoRequest
import com.example.sistemaobras.entity.PerfilUsuario
import com.example.sistemaobras.entity.PerfilUsuarioConverter
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.NaoProcessavelException
import com.example.sistemaobras.exception.ProibidoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.repository.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Parte 6B: horário do evento capturado no celular (uso offline).
class Parte6BTest {

    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

    // Mockito.eq() devolve null; o Kotlin barraria null num parâmetro não nulo.
    private fun <T> igual(valor: T): T {
        Mockito.eq(valor)
        return valor
    }

    private val agora: LocalDateTime = LocalDateTime.of(2026, 10, 19, 15, 0)

    private fun comoMotorista(login: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(login, null, listOf(SimpleGrantedAuthority("ROLE_MOTORISTA")))
    }

    @AfterEach
    fun limparToken() = SecurityContextHolder.clearContext()

    private fun diario(
        dono: String = "ana", status: String = "aberto",
        abertoEm: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).minusHours(3), fechadoEm: LocalDateTime? = null,
        veiculoId: UUID = UUID.randomUUID(), idLocalFechamento: UUID? = null, medidorInicial: Double = 100.0
    ) = DiarioDoEvento.Diario(UUID.randomUUID(), dono, status, abertoEm, fechadoEm, veiculoId, null, idLocalFechamento, medidorInicial)

    // ===================== Horário do evento =====================

    @Test
    fun `ocorridoEm com fuso vira UTC`() {
        assertEquals(LocalDateTime.of(2026, 10, 19, 12, 0), HorarioEvento.resolver("2026-10-19T12:00:00Z", agora = agora).ocorrido)
        assertEquals(LocalDateTime.of(2026, 10, 19, 12, 0), HorarioEvento.resolver("2026-10-19T09:00:00-03:00", agora = agora).ocorrido)
        assertFalse(HorarioEvento.resolver("2026-10-19T12:00:00Z", agora = agora).suspeito)
    }

    @Test
    fun `sem ocorridoEm vale o registradoEm em ms e depois a hora do servidor`() {
        val ms = LocalDateTime.of(2026, 10, 19, 11, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(LocalDateTime.of(2026, 10, 19, 11, 0), HorarioEvento.resolver(null, ms, agora = agora).ocorrido)
        assertEquals(agora, HorarioEvento.resolver(null, agora = agora).ocorrido)
    }

    @Test
    fun `horario sem fuso e recusado com 400`() {
        val erro = assertFailsWith<RegraNegocioException> { HorarioEvento.resolver("2026-10-19T12:00:00", agora = agora) }
        assertEquals("HORARIO_INVALIDO", erro.codigo)
    }

    @Test
    fun `futuro ate 24 h so marca e acima de 24 h recusa com 422`() {
        assertEquals(listOf("HORARIO_FUTURO"), HorarioEvento.resolver("2026-10-19T18:00:00Z", agora = agora).motivos)
        val erro = assertFailsWith<NaoProcessavelException> { HorarioEvento.resolver("2026-10-21T15:00:00Z", agora = agora) }
        assertEquals("HORARIO_FUTURO", erro.codigo)
    }

    @Test
    fun `mais de 7 dias atras e desvio do relogio so marcam`() {
        assertEquals(listOf("HORARIO_ANTIGO"), HorarioEvento.resolver("2026-10-10T12:00:00Z", agora = agora).motivos)
        val comDesvio = HorarioEvento.resolver("2026-10-19T14:50:00Z", enviadoEm = "2026-10-19T14:50:00Z", agora = agora)
        assertEquals(600, comDesvio.desvioSeg)
        assertEquals(listOf("DESVIO_RELOGIO"), comDesvio.motivos)
    }

    @Test
    fun `evento fora do periodo do diario so marca`() {
        val h = HorarioEvento.resolver("2026-10-19T10:00:00Z", agora = agora)
        val fora = h.conferirJanela(LocalDateTime.of(2026, 10, 19, 11, 0), LocalDateTime.of(2026, 10, 19, 13, 0), "FORA_DA_JANELA_DIARIO")
        assertEquals("FORA_DA_JANELA_DIARIO", fora.motivoTexto)
        val dentro = h.conferirJanela(LocalDateTime.of(2026, 10, 19, 9, 0), null, "FORA_DA_JANELA_DIARIO")
        assertFalse(dentro.suspeito)
    }

    // ===================== Turno =====================

    private val turnoRepository = mock(TurnoRepository::class.java)
    private val diarioRepository = mock(DiarioBordoRepository::class.java)
    private val turnoService = TurnoService(turnoRepository, mock(UsuarioRepository::class.java), diarioRepository)
    private val idLocal = UUID.randomUUID()

    @Test
    fun `reenvio de abrir turno com o mesmo idLocal devolve o turno sem gravar de novo`() {
        given(turnoRepository.buscarPorIdLocalAbertura(idLocal.toString())).willReturn(listOf(
            arrayOf<Any?>(UUID.randomUUID().toString(), "ana", "aberto", "2026-10-19 08:00:00", null, idLocal.toString(), null)
        ))
        val resposta = turnoService.abrirTurno(TurnoRequest("ana", idLocal = idLocal.toString()))
        assertEquals("aberto", resposta.status)
        verify(turnoRepository, never()).abrirTurno(anyString(), qualquer(), qualquer(), qualquer(), anyBoolean(), qualquer())
    }

    @Test
    fun `idLocal de turno de outro motorista responde 409`() {
        given(turnoRepository.buscarPorIdLocalAbertura(idLocal.toString())).willReturn(listOf(
            arrayOf<Any?>(UUID.randomUUID().toString(), "beto", "aberto", "2026-10-19 08:00:00", null, idLocal.toString(), null)
        ))
        val erro = assertFailsWith<ConflitoException> { turnoService.abrirTurno(TurnoRequest("ana", idLocal = idLocal.toString())) }
        assertEquals("ID_LOCAL_EM_USO", erro.codigo)
    }

    // ===================== Diário: fechar por id =====================

    private val diarioDoEvento = mock(DiarioDoEvento::class.java)
    private val diarioService = DiarioBordoService(
        diarioRepository, turnoRepository, mock(VeiculoRepository::class.java), mock(LogService::class.java),
        mock(AssinaturaRepository::class.java), diarioDoEvento
    )

    @Test
    fun `fechar por id grava fechado_em com a hora do evento`() {
        val d = diario()
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)
        given(diarioRepository.fecharDiario(anyString(), anyDouble(), qualquer(), qualquer(), qualquer(), qualquer(), qualquer(), anyBoolean(), qualquer()))
            .willReturn(1)
        val evento = LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusMinutes(30)

        val resposta = diarioService.fecharDiarioPorId(
            d.id.toString(), FecharDiarioRequest(medidorFinal = 150.0, ocorridoEm = "${evento}Z", idLocal = idLocal.toString())
        )

        assertEquals(evento, resposta.fechadoEm)
        verify(diarioRepository).fecharDiario(
            igual(d.id.toString()), Mockito.eq(150.0), isNull(), igual(evento), qualquer(), igual(idLocal.toString()),
            isNull(), Mockito.eq(false), isNull()
        )
    }

    @Test
    fun `fechar diario ja fechado responde 409 e reenvio com o mesmo idLocal devolve o diario`() {
        val fechado = diario(status = "fechado", idLocalFechamento = idLocal)
        given(diarioDoEvento.porId(fechado.id.toString())).willReturn(fechado)
        val erro = assertFailsWith<ConflitoException> {
            diarioService.fecharDiarioPorId(fechado.id.toString(), FecharDiarioRequest(medidorFinal = 150.0, idLocal = UUID.randomUUID().toString()))
        }
        assertEquals("DIARIO_JA_FECHADO", erro.codigo)

        given(diarioRepository.buscarDetalhe(fechado.id.toString())).willReturn(listOf(arrayOf<Any?>(
            fechado.id.toString(), "Caminhão", null, "Ana", "100", "150", "50", null, "fechado",
            "2026-10-19 08:00:00", "2026-10-19 12:00:00", fechado.veiculoId.toString(), "hodometro"
        )))
        val reenvio = diarioService.fecharDiarioPorId(fechado.id.toString(), FecharDiarioRequest(medidorFinal = 150.0, idLocal = idLocal.toString()))
        assertEquals("fechado", reenvio.status)
    }

    @Test
    fun `fechar diario de outro motorista responde 403`() {
        comoMotorista("beto")
        val real = DiarioDoEvento(diarioRepository)
        val d = diario(dono = "ana")
        assertFailsWith<ProibidoException> { real.conferirDono(d) }
    }

    @Test
    fun `outro envio fechou o diario antes do UPDATE responde 409`() {
        val d = diario()
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)
        // fecharDiario devolve 0 (nenhuma linha alterada) por padrão no mock
        val erro = assertFailsWith<ConflitoException> {
            diarioService.fecharDiarioPorId(d.id.toString(), FecharDiarioRequest(medidorFinal = 150.0))
        }
        assertEquals("DIARIO_JA_FECHADO", erro.codigo)
    }

    // ===================== Abastecimento =====================

    private val abastecimentoRepository = mock(AbastecimentoRepository::class.java)
    private val abastecimentoService = AbastecimentoService(
        abastecimentoRepository, diarioRepository, mock(UsuarioRepository::class.java), mock(LogService::class.java), diarioDoEvento
    )

    @Test
    fun `abastecimento em diario assinado responde 409`() {
        val assinado = diario(status = "assinado")
        given(diarioDoEvento.porId(assinado.id.toString())).willReturn(assinado)
        given(diarioDoEvento.conferirNaoAssinado(assinado)).willCallRealMethod()
        val erro = assertFailsWith<ConflitoException> {
            abastecimentoService.registrar(AbastecimentoRequest(
                loginMotorista = "ana", tipoCombustivel = "diesel", litros = 10.0, valorTotal = 50.0, diarioId = assinado.id.toString()
            ))
        }
        assertEquals("DIARIO_ASSINADO", erro.codigo)
    }

    @Test
    fun `comboio offline vai para o diario do veiculo no horario do evento mesmo ja fechado`() {
        comoMotorista("carlos")
        val veiculo = UUID.randomUUID()
        val fechadoDepois = diario(dono = "ana", status = "fechado", veiculoId = veiculo,
            abertoEm = LocalDateTime.now(ZoneOffset.UTC).minusHours(5), fechadoEm = LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
        given(diarioDoEvento.porVeiculoNoHorario(igual(veiculo.toString()), qualquer())).willReturn(fechadoDepois)
        given(diarioDoEvento.motoristaDirigiaComboio(igual("carlos"), qualquer())).willReturn(true)
        val evento = LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(2)

        val resposta = abastecimentoService.registrar(AbastecimentoRequest(
            loginMotorista = "carlos", tipoCombustivel = "diesel", litros = 30.0, valorTotal = 0.0,
            veiculoAbastecidoId = veiculo.toString(), ocorridoEm = "${evento}Z"
        ))

        assertTrue(resposta.comboio)
        assertFalse(resposta.horarioSuspeito)
        verify(abastecimentoRepository).inserirAbastecimento(
            anyString(), igual(fechadoDepois.id.toString()), igual("diesel"), Mockito.eq(30.0), Mockito.eq(0.0), isNull(),
            igual(veiculo.toString()), igual(evento), qualquer(), isNull(), isNull(), Mockito.eq(false), isNull(), igual("carlos")
        )
    }

    @Test
    fun `idLocal de abastecimento de outro motorista responde 409`() {
        given(abastecimentoRepository.buscarPorIdLocal(idLocal.toString())).willReturn(listOf(arrayOf<Any?>(UUID.randomUUID().toString(), "beto", "beto")))
        val erro = assertFailsWith<ConflitoException> {
            abastecimentoService.registrar(AbastecimentoRequest(
                loginMotorista = "ana", tipoCombustivel = "diesel", litros = 10.0, valorTotal = 50.0, idLocal = idLocal.toString()
            ))
        }
        assertEquals("ID_LOCAL_EM_USO", erro.codigo)
    }

    // ===================== Ocorrência =====================

    @Test
    fun `ocorrencia com diarioId de outro motorista responde 403`() {
        val d = diario(dono = "beto")
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)
        given(diarioDoEvento.conferirDono(d)).willThrow(ProibidoException("Este diário é de outro motorista.", "DIARIO_DE_OUTRO_MOTORISTA"))
        val service = OcorrenciaService(
            mock(OcorrenciaRepository::class.java), diarioRepository, mock(UsuarioRepository::class.java),
            mock(VeiculoRepository::class.java), mock(ManutencaoRepository::class.java), mock(LogService::class.java), diarioDoEvento
        )
        val erro = assertFailsWith<ProibidoException> {
            service.registrar(OcorrenciaRequest(loginMotorista = "ana", tipo = "pneu", descricao = "furado", diarioId = d.id.toString()))
        }
        assertEquals("DIARIO_DE_OUTRO_MOTORISTA", erro.codigo)
    }

    // ===================== Rastreamento em lote =====================

    private val rastreamentoRepository = mock(RastreamentoRepository::class.java)
    private val rastreamentoService = RastreamentoService(rastreamentoRepository, diarioRepository, diarioDoEvento)

    @Test
    fun `lote com mais de 200 pontos responde 400`() {
        val erro = assertFailsWith<RegraNegocioException> {
            rastreamentoService.registrarLote(RastreamentoLoteRequest("x", pontos = List(201) { PontoLoteRequest(0.0, 0.0) }))
        }
        assertEquals("LOTE_GRANDE_DEMAIS", erro.codigo)
    }

    @Test
    fun `lote devolve o resultado de cada ponto sem derrubar os outros`() {
        val d = diario()
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)
        val repetido = UUID.randomUUID()
        given(rastreamentoRepository.buscarPorIdLocal(repetido.toString())).willReturn(listOf(arrayOf<Any?>(UUID.randomUUID().toString(), d.id.toString())))
        given(rastreamentoRepository.contarProximosNoDiario(igual(d.id.toString()), Mockito.eq(2.0), Mockito.eq(2.0), qualquer())).willReturn(1L)
        val futuroDemais = LocalDateTime.now(ZoneOffset.UTC).withNano(0).plusDays(3)

        val r = rastreamentoService.registrarLote(RastreamentoLoteRequest(d.id.toString(), pontos = listOf(
            PontoLoteRequest(1.0, 1.0, idLocal = UUID.randomUUID().toString()),   // gravado
            PontoLoteRequest(1.5, 1.5, idLocal = repetido.toString()),            // duplicado
            PontoLoteRequest(2.0, 2.0),                                           // descartado (perto de outro)
            PontoLoteRequest(3.0, 3.0, ocorridoEm = "${futuroDemais}Z")           // recusado (HORARIO_FUTURO)
        )))

        assertEquals(listOf("gravado", "duplicado", "descartado_proximo", "recusado"), r.pontos.map { it.resultado })
        assertEquals("HORARIO_FUTURO", r.pontos[3].codigo)
        assertEquals(1, r.gravados)
        assertEquals(1, r.recusados)
    }

    // ===================== Perfil tolerante =====================

    @Test
    fun `perfil desconhecido no banco nao derruba a leitura`() {
        val conversor = PerfilUsuarioConverter()
        assertEquals(PerfilUsuario.administrador, conversor.convertToEntityAttribute("administrador"))
        assertEquals(PerfilUsuario.desconhecido, conversor.convertToEntityAttribute("auditor"))
        assertNull(conversor.convertToEntityAttribute(null))
    }
}
