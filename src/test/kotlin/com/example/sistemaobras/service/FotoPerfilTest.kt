package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AtualizarPerfilRequest
import com.example.sistemaobras.dto.AtualizarUsuarioRequest
import com.example.sistemaobras.entity.Usuario
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.repository.SecretariaRepository
import com.example.sistemaobras.repository.UsuarioRepository
import com.example.sistemaobras.security.JwtService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.util.unit.DataSize
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// Foto de perfil: normalização na escrita (base64 estrito, tipo pelos bytes, teto).
class FotoPerfilTest {

    private val max = 512L * 1024
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 5)
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun codigo(texto: String) = assertFailsWith<RegraNegocioException> { FotoPerfil.normalizar(texto, max) }.codigo

    @Test
    fun `jpeg com quebras de linha e espacos ganha o prefixo`() {
        val comQuebras = b64(jpeg).chunked(4).joinToString("\n ") + "\r\n"
        assertEquals("data:image/jpeg;base64,${b64(jpeg)}", FotoPerfil.normalizar(comQuebras, max))
    }

    @Test
    fun `png sem prefixo ganha o prefixo png`() {
        assertEquals("data:image/png;base64,${b64(png)}", FotoPerfil.normalizar(b64(png), max))
    }

    @Test
    fun `prefixo jpeg fica igual, jpg vira jpeg e prefixo errado e corrigido`() {
        val certo = "data:image/jpeg;base64,${b64(jpeg)}"
        assertEquals(certo, FotoPerfil.normalizar(certo, max))
        assertEquals(certo, FotoPerfil.normalizar("data:image/jpg;base64,${b64(jpeg)}", max))
        assertEquals(certo, FotoPerfil.normalizar("data:image/png;base64,${b64(jpeg)}", max))
    }

    @Test
    fun `grava o base64 recodificado dos bytes, sem padding faltando`() {
        val semPadding = b64(png).trimEnd('=')
        assertEquals(true, b64(png).endsWith("="))
        assertEquals("data:image/png;base64,${b64(png)}", FotoPerfil.normalizar(semPadding, max))
    }

    @Test
    fun `gif, pdf, webp e texto comum sao recusados`() {
        assertEquals("FOTO_PERFIL_INVALIDA", codigo(b64("GIF89a....".toByteArray())))
        assertEquals("FOTO_PERFIL_INVALIDA", codigo(b64("%PDF-1.4 ...".toByteArray())))
        assertEquals("FOTO_PERFIL_INVALIDA", codigo("data:image/webp;base64,${b64(jpeg)}"))
        assertEquals("FOTO_PERFIL_INVALIDA", codigo("data:image/png;base64,"))
    }

    @Test
    fun `lixo no meio do base64 e recusado pelo decoder estrito`() {
        val b = b64(jpeg)
        assertEquals("FOTO_PERFIL_INVALIDA", codigo(b.substring(0, 4) + "*#" + b.substring(4)))
        assertEquals("FOTO_PERFIL_INVALIDA", codigo("/9j/a"))      // começa como jpeg, mas o base64 está truncado
    }

    @Test
    fun `teto vale sobre a imagem decodificada`() {
        val noLimite = jpeg + ByteArray((max - jpeg.size).toInt())
        assertEquals(true, FotoPerfil.normalizar(b64(noLimite), max)!!.startsWith("data:image/jpeg;base64,"))
        assertEquals("FOTO_PERFIL_GRANDE_DEMAIS", codigo(b64(noLimite + byteArrayOf(0))))
        assertEquals("FOTO_PERFIL_GRANDE_DEMAIS", codigo("A".repeat(2_000_000)))
    }

    @Test
    fun `vazia ou null nao altera`() {
        assertNull(FotoPerfil.normalizar(null, max))
        assertNull(FotoPerfil.normalizar("", max))
        assertNull(FotoPerfil.normalizar("  \n ", max))
    }

    // ---------- UsuarioService ----------

    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

    private val usuarioRepository = mock(UsuarioRepository::class.java)
    private val service = UsuarioService(
        usuarioRepository, mock(JwtService::class.java), mock(LogService::class.java),
        mock(SecretariaRepository::class.java), maxFotoPerfil = DataSize.ofKilobytes(512)
    )

    private fun existe() = given(usuarioRepository.findByLogin("joao")).willReturn(Usuario(login = "joao", nomeCompleto = "João"))

    @Test
    fun `perfil grava a foto normalizada e foto vazia nao chama a gravacao`() {
        existe()
        service.atualizarPerfil("joao", AtualizarPerfilRequest(fotoPerfil = b64(jpeg).chunked(4).joinToString("\n")))
        verify(usuarioRepository).atualizarFoto("joao", "data:image/jpeg;base64,${b64(jpeg)}")

        Mockito.clearInvocations(usuarioRepository)
        service.atualizarPerfil("joao", AtualizarPerfilRequest(fotoPerfil = ""))
        verify(usuarioRepository, never()).atualizarFoto(anyString(), anyString())
    }

    @Test
    fun `foto invalida no perfil nao grava nem o nome`() {
        existe()
        assertFailsWith<RegraNegocioException> {
            service.atualizarPerfil("joao", AtualizarPerfilRequest(fotoPerfil = "lixo!", nomeCompleto = "Outro"))
        }
        verify(usuarioRepository, never()).atualizarFoto(anyString(), anyString())
        verify(usuarioRepository, never()).atualizarNome(anyString(), anyString())
    }

    @Test
    fun `atualizar usuario com foto vazia manda null e mantem a foto`() {
        existe()
        service.atualizarUsuario("joao", AtualizarUsuarioRequest(fotoPerfil = " "))
        verify(usuarioRepository).atualizarUsuarioCompleto("joao", null, null, null, null, null, null, null, null)
    }

    @Test
    fun `atualizar usuario com foto invalida nao grava nada`() {
        existe()
        assertFailsWith<RegraNegocioException> {
            service.atualizarUsuario("joao", AtualizarUsuarioRequest(fotoPerfil = b64("GIF89a".toByteArray())))
        }
        verify(usuarioRepository, never()).atualizarUsuarioCompleto(anyString(), qualquer(), qualquer(), qualquer(), qualquer(), qualquer(), qualquer(), qualquer(), qualquer())
    }
}
