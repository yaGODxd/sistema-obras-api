package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AbrirDiarioRequest
import com.example.sistemaobras.dto.PecaRequest
import com.example.sistemaobras.dto.TurnoRequest
import com.example.sistemaobras.entity.DiarioBordo
import com.example.sistemaobras.entity.Manutencao
import com.example.sistemaobras.entity.ManutencaoPeca
import com.example.sistemaobras.entity.Veiculo
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.repository.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Parte 5: bloqueios no servidor (itens 9 e 15) e os opcionais aprovados da Parte 4.
class Parte5RegrasTest {

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

    // ---------- Abrir diário ----------

    private val diarioRepository = mock(DiarioBordoRepository::class.java)
    private val turnoRepository = mock(TurnoRepository::class.java)
    private val assinaturaRepository = mock(AssinaturaRepository::class.java)
    private val diarioDoEvento = mock(DiarioDoEvento::class.java)
    private val diarioService = DiarioBordoService(
        diarioRepository, turnoRepository, mock(VeiculoRepository::class.java), mock(LogService::class.java),
        assinaturaRepository, diarioDoEvento
    )
    private val abrir = AbrirDiarioRequest(loginMotorista = "ana", veiculoId = UUID.randomUUID().toString(), medidorInicial = 10.0)
    private val sairDeServico = TurnoRequest(login = "ana")

    // Turno aberto (temTurnoAberto para abrir diário; buscarTurnoAberto para sair de serviço).
    private fun motoristaEmServico() {
        given(turnoRepository.temTurnoAberto("ana")).willReturn(1L)
        given(turnoRepository.buscarTurnoAberto("ana")).willReturn(listOf(
            arrayOf<Any?>(UUID.randomUUID().toString(), "ana", "aberto", "2026-10-19 08:00:00", null, null, null)
        ))
    }

    private fun naoAbriuDiario() = verify(diarioRepository, never()).abrirDiario(
        anyString(), anyString(), qualquer(), qualquer(), anyDouble(), qualquer(), qualquer(), qualquer(), qualquer(), anyBoolean(), qualquer()
    )

    private fun fechouTurno(vezes: Int) = verify(turnoRepository, Mockito.times(vezes)).fecharTurno(
        anyString(), qualquer(), qualquer(), qualquer(), anyBoolean(), qualquer()
    )

    @Test
    fun `abrir diario sem PIN e recusado`() {
        motoristaEmServico()
        val erro = assertFailsWith<RegraNegocioException> { diarioService.abrirDiario(abrir) }
        assertEquals("Cadastre seu PIN de assinatura antes de abrir um diário.", erro.message)
        assertEquals("PIN_AUSENTE", erro.codigo)
        naoAbriuDiario()
    }

    @Test
    fun `abrir diario com outro pendente de assinatura e recusado`() {
        motoristaEmServico()
        given(assinaturaRepository.contarPinDefinido("ana")).willReturn(1L)
        given(diarioRepository.contarPendentesDeAssinatura("ana")).willReturn(1L)
        val erro = assertFailsWith<RegraNegocioException> { diarioService.abrirDiario(abrir) }
        assertEquals("Você tem um diário fechado aguardando assinatura. Assine-o antes de abrir outro.", erro.message)
        assertEquals("DIARIO_PENDENTE_ASSINATURA", erro.codigo)
    }

    // ---------- Interruptor APP_BLOQUEIOS_DIARIO ----------

    @Test
    fun `com o interruptor ligado por padrao os bloqueios valem`() {
        // diarioService e turnoService deste teste usam o padrão (ligado).
        motoristaEmServico()
        assertFailsWith<RegraNegocioException> { diarioService.abrirDiario(abrir) }
        given(diarioRepository.temDiarioAberto("ana")).willReturn(1L)
        assertFailsWith<RegraNegocioException> { turnoService.fecharTurno(sairDeServico) }
    }

    @Test
    fun `com o interruptor desligado abre diario sem PIN e com pendente`() {
        val semBloqueios = DiarioBordoService(
            diarioRepository, turnoRepository, mock(VeiculoRepository::class.java), mock(LogService::class.java),
            assinaturaRepository, diarioDoEvento, bloqueiosAtivos = false
        )
        motoristaEmServico()
        given(diarioRepository.contarPendentesDeAssinatura("ana")).willReturn(3L)   // e sem PIN (padrão 0)
        given(diarioRepository.findDiarioAberto("ana")).willReturn(DiarioBordo(id = UUID.randomUUID(), veiculoId = UUID.randomUUID()))

        semBloqueios.abrirDiario(abrir)

        verify(diarioRepository).abrirDiario(
            igual("ana"), igual(abrir.veiculoId), isNull(), isNull(), eq(10.0), qualquer(), qualquer(), isNull(), isNull(), eq(false), isNull()
        )
    }

    @Test
    fun `com o interruptor desligado sai de servico com diario aberto`() {
        val semBloqueios = TurnoService(turnoRepository, mock(UsuarioRepository::class.java), diarioRepository, bloqueiosAtivos = false)
        motoristaEmServico()
        given(diarioRepository.temDiarioAberto("ana")).willReturn(1L)
        semBloqueios.fecharTurno(sairDeServico)
        fechouTurno(1)
    }

    // ---------- Sair de serviço ----------

    private val turnoService = TurnoService(turnoRepository, mock(UsuarioRepository::class.java), diarioRepository)

    @Test
    fun `sair de servico com diario aberto e recusado`() {
        motoristaEmServico()
        given(diarioRepository.temDiarioAberto("ana")).willReturn(1L)
        val erro = assertFailsWith<RegraNegocioException> { turnoService.fecharTurno(sairDeServico) }
        assertEquals("Você tem um diário aberto. Feche o diário antes de sair de serviço.", erro.message)
        assertEquals("TURNO_COM_DIARIO_ABERTO", erro.codigo)
        fechouTurno(0)
    }

    @Test
    fun `sair de servico com diario so pendente de assinatura e permitido`() {
        motoristaEmServico()
        given(diarioRepository.contarPendentesDeAssinatura("ana")).willReturn(1L)
        turnoService.fecharTurno(sairDeServico)
        fechouTurno(1)
    }

    // ---------- Editar peça ----------

    private val manutencaoRepository = mock(ManutencaoRepository::class.java)
    private val pecaRepository = mock(ManutencaoPecaRepository::class.java)
    private val manutencaoService = ManutencaoService(
        manutencaoRepository, pecaRepository, mock(VeiculoRepository::class.java), diarioRepository,
        mock(OcorrenciaService::class.java), mock(LogService::class.java)
    )
    private val pecaId = UUID.randomUUID()
    private val manutencaoId = UUID.randomUUID()
    private val novaPeca = PecaRequest(nomePeca = "Pastilha", quantidade = 4, valorUnitario = 0.0)

    private fun pecaDeManutencao(status: String) {
        given(pecaRepository.findById(pecaId)).willReturn(Optional.of(
            ManutencaoPeca(id = pecaId, manutencaoId = manutencaoId, nomePeca = "Velha", quantidade = 1, valorUnitario = BigDecimal.TEN)
        ))
        given(manutencaoRepository.findById(manutencaoId)).willReturn(Optional.of(
            Manutencao(id = manutencaoId, tipo = "corretiva", descricao = "Freio", status = status)
        ))
        given(pecaRepository.save(qualquer<ManutencaoPeca>())).willAnswer { it.arguments[0] }
    }

    @Test
    fun `editar peca atualiza os dados`() {
        pecaDeManutencao("em_andamento")
        val resposta = manutencaoService.atualizarPeca(pecaId.toString(), novaPeca)
        assertEquals("Pastilha", resposta.nomePeca)
        assertEquals(4, resposta.quantidade)
        assertEquals(0.0, resposta.valorTotal)
    }

    @Test
    fun `editar peca de manutencao concluida responde conflito`() {
        pecaDeManutencao("concluida")
        assertFailsWith<ConflitoException> { manutencaoService.atualizarPeca(pecaId.toString(), novaPeca) }
    }

    @Test
    fun `editar peca inexistente ou com id invalido`() {
        given(pecaRepository.findById(pecaId)).willReturn(Optional.empty())
        assertFailsWith<NaoEncontradoException> { manutencaoService.atualizarPeca(pecaId.toString(), novaPeca) }
        assertFailsWith<RegraNegocioException> { manutencaoService.atualizarPeca("abc", novaPeca) }
    }

    // ---------- Reativar veículo ----------

    private val veiculoRepository = mock(VeiculoRepository::class.java)
    private val veiculoService = VeiculoService(veiculoRepository)
    private val veiculoId = UUID.randomUUID()

    private fun veiculoInativo() = given(veiculoRepository.findById(veiculoId))
        .willReturn(Optional.of(Veiculo(id = veiculoId, placa = "abc-1d23", status = "inativo")))

    @Test
    fun `reativar veiculo volta como disponivel`() {
        veiculoInativo()
        veiculoService.alterarAtivo(veiculoId.toString(), true)
        verify(veiculoRepository).atualizarStatus(veiculoId.toString(), "disponivel")
    }

    @Test
    fun `reativar veiculo com placa ja usada por outro ativo e recusado`() {
        veiculoInativo()
        given(veiculoRepository.contarPorPlaca("ABC1D23", veiculoId.toString())).willReturn(1L)
        val erro = assertFailsWith<RegraNegocioException> { veiculoService.alterarAtivo(veiculoId.toString(), true) }
        assertEquals("Já existe um veículo ativo com esta placa.", erro.message)
        verify(veiculoRepository, never()).atualizarStatus(anyString(), anyString())
    }
}
