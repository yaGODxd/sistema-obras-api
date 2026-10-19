package com.example.sistemaobras.service

import com.example.sistemaobras.dto.LoginRequest
import com.example.sistemaobras.entity.Usuario
import com.example.sistemaobras.exception.NaoAutorizadoException
import com.example.sistemaobras.repository.SecretariaRepository
import com.example.sistemaobras.repository.UsuarioRepository
import com.example.sistemaobras.security.JwtService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UsuarioServiceLoginTest {

    private val usuarioRepository = mock(UsuarioRepository::class.java)
    private val service = UsuarioService(
        usuarioRepository,
        mock(JwtService::class.java),
        mock(LogService::class.java),
        mock(SecretariaRepository::class.java)
    )
    private val hash = BCryptPasswordEncoder().encode("certa")!!

    private fun erroDoLogin(login: String, senha: String): String =
        assertFailsWith<NaoAutorizadoException> { service.login(LoginRequest(login, senha)) }.message!!

    @Test
    fun `usuario inexistente e senha errada tem a mesma mensagem`() {
        given(usuarioRepository.findByLogin("joao")).willReturn(Usuario(login = "joao", senhaHash = hash))
        assertEquals("Login ou senha incorretos.", erroDoLogin("ninguem", "x"))
        assertEquals("Login ou senha incorretos.", erroDoLogin("joao", "errada"))
    }

    @Test
    fun `inativo so e revelado com a senha certa`() {
        given(usuarioRepository.findByLogin("ana")).willReturn(Usuario(login = "ana", senhaHash = hash, ativo = false))
        assertEquals("Login ou senha incorretos.", erroDoLogin("ana", "errada"))
        assertEquals("Usuário inativo. Procure o gestor.", erroDoLogin("ana", "certa"))
    }
}
