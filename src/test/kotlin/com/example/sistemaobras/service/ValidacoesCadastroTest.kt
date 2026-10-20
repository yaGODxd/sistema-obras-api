package com.example.sistemaobras.service

import com.example.sistemaobras.dto.CriarUsuarioRequest
import com.example.sistemaobras.dto.VeiculoRequest
import com.example.sistemaobras.entity.Veiculo
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.repository.SecretariaRepository
import com.example.sistemaobras.repository.UsuarioRepository
import com.example.sistemaobras.repository.VeiculoRepository
import com.example.sistemaobras.security.JwtService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidacoesCadastroTest {

    // ---------- CPF ----------

    @Test
    fun `digitos verificadores do CPF`() {
        assertTrue(ValidadorCpf.valido("529.982.247-25"))
        assertTrue(ValidadorCpf.valido("52998224725"))
        assertFalse(ValidadorCpf.valido("529.982.247-24"))
        assertFalse(ValidadorCpf.valido("111.111.111-11"))
        assertFalse(ValidadorCpf.valido("123"))
    }

    private val usuarioRepository = mock(UsuarioRepository::class.java)
    private val jwtService = mock(JwtService::class.java)

    private fun usuarioService(validarCpf: Boolean) = UsuarioService(
        usuarioRepository, jwtService, mock(LogService::class.java), mock(SecretariaRepository::class.java), validarCpf
    )

    private fun cadastro(cpf: String) = CriarUsuarioRequest(nomeCompleto = "Ana", cpf = cpf, login = "ana", senha = "s")

    @Test
    fun `CPF repetido e recusado comparando so os digitos`() {
        given(usuarioRepository.contarPorCpf("52998224725")).willReturn(1L)
        val erro = assertFailsWith<ConflitoException> { usuarioService(false).criar(cadastro("529.982.247-25")) }
        assertEquals("Já existe um usuário com este CPF.", erro.message)
    }

    @Test
    fun `CPF com digitos errados so e recusado com o interruptor ligado`() {
        given(jwtService.gerarToken("ana", "motorista")).willReturn("token")
        assertEquals("token", usuarioService(false).criar(cadastro("123.456.789-00")).token)

        val erro = assertFailsWith<RegraNegocioException> { usuarioService(true).criar(cadastro("123.456.789-00")) }
        assertEquals("CPF inválido.", erro.message)
    }

    // ---------- Placa ----------

    private val veiculoRepository = mock(VeiculoRepository::class.java)
    private val veiculoService = VeiculoService(veiculoRepository)
    private val id = "11111111-1111-1111-1111-111111111111"

    private fun veiculo(placa: String?) = VeiculoRequest(tipoId = id, placa = placa, descricao = "Caminhão")

    @Test
    fun `placa repetida e recusada sem diferenciar traco e minusculas`() {
        given(veiculoRepository.statusDosVeiculosComPlaca("ABC1D23", null)).willReturn(listOf("disponivel"))
        val erro = assertFailsWith<ConflitoException> { veiculoService.cadastrar(veiculo("abc-1d23")) }
        assertEquals("Já existe um veículo com esta placa.", erro.message)
    }

    @Test
    fun `na edicao o proprio veiculo nao conta como repetido`() {
        given(veiculoRepository.findById(UUID.fromString(id))).willReturn(Optional.of(Veiculo()))
        veiculoService.atualizar(id, veiculo("ABC1D23"))
        verify(veiculoRepository).statusDosVeiculosComPlaca("ABC1D23", id)
    }

    @Test
    fun `veiculo sem placa nao confere unicidade`() {
        veiculoService.cadastrar(veiculo(""))
        veiculoService.cadastrar(veiculo(null))
        verify(veiculoRepository, never()).statusDosVeiculosComPlaca(anyString(), isNull())
    }

    @Test
    fun `placa de veiculo inativo pede para reativar`() {
        given(veiculoRepository.statusDosVeiculosComPlaca("ABC1D23", null)).willReturn(listOf("inativo"))
        val erro = assertFailsWith<ConflitoException> { veiculoService.cadastrar(veiculo("abc 1d23")) }
        assertEquals("Já existe um veículo inativo com esta placa. Reative-o em vez de cadastrar novo.", erro.message)
        assertEquals("PLACA_DE_VEICULO_INATIVO", erro.codigo)
    }
}
