package com.example.sistemaobras.service

import com.example.sistemaobras.exception.ApiException
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.ProibidoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.repository.FotoRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Parte 9, etapa 2: POST /fotos.
class FotoServiceTest {

    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

    private val fotoRepository = mock(FotoRepository::class.java)
    private val diarioDoEvento = mock(DiarioDoEvento::class.java)
    private val service = FotoService(fotoRepository, diarioDoEvento, ArmazenamentoFotosNoBanco())

    private val jpeg = MockMultipartFile("arquivo", "foto.jpg", "image/jpeg",
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))
    private val pdfComNomeDeJpg = MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "%PDF-1.4 ...".toByteArray())

    private fun comoMotorista(login: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(login, null, listOf(SimpleGrantedAuthority("ROLE_MOTORISTA")))
    }

    @AfterEach
    fun limpar() = SecurityContextHolder.clearContext()

    private fun diario(dono: String = "ana", status: String = "aberto") = DiarioDoEvento.Diario(
        UUID.randomUUID(), dono, status, LocalDateTime.now(ZoneOffset.UTC).minusHours(3), null, UUID.randomUUID(), null, null, 0.0
    )

    private fun naoGravou() = verify(fotoRepository, never()).inserir(qualquer())

    @Test
    fun `foto do medidor grava os bytes, o tipo e o hash`() {
        val d = diario()
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)

        val r = service.enviar(jpeg, FotoService.EnvioFoto(diarioId = d.id.toString(), tipo = "medidor_abertura"))

        assertEquals("image/jpeg", r.tipoMime)
        assertEquals(7, r.tamanhoBytes)
        assertEquals(FotoService.sha256(jpeg.bytes), r.hashSha256)
        verify(fotoRepository).inserir(qualquer())
    }

    @Test
    fun `reenvio com o mesmo idLocal devolve a foto sem gravar de novo`() {
        comoMotorista("ana")
        val idLocal = UUID.randomUUID()
        val existente = FotoRepository.FotoMetadados(
            UUID.randomUUID(), UUID.randomUUID(), "livre", null, null, null, null, 7, "image/jpeg", "abc", idLocal, false, null, "ana"
        )
        given(fotoRepository.buscarPorIdLocal(idLocal)).willReturn(existente)

        val r = service.enviar(jpeg, FotoService.EnvioFoto(diarioId = UUID.randomUUID().toString(), tipo = "livre", idLocal = idLocal.toString()))

        assertEquals(existente.id, r.id)
        naoGravou()
    }

    @Test
    fun `idLocal de foto de outro motorista responde 409`() {
        comoMotorista("ana")
        val idLocal = UUID.randomUUID()
        given(fotoRepository.buscarPorIdLocal(idLocal)).willReturn(FotoRepository.FotoMetadados(
            UUID.randomUUID(), UUID.randomUUID(), "livre", null, null, null, null, 7, "image/jpeg", "abc", idLocal, false, null, "beto"
        ))
        val erro = assertFailsWith<ConflitoException> {
            service.enviar(jpeg, FotoService.EnvioFoto(diarioId = UUID.randomUUID().toString(), tipo = "livre", idLocal = idLocal.toString()))
        }
        assertEquals("ID_LOCAL_EM_USO", erro.codigo)
    }

    @Test
    fun `foto de diario assinado responde 409`() {
        val d = diario(status = "assinado")
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)
        given(diarioDoEvento.conferirNaoAssinado(d)).willCallRealMethod()
        val erro = assertFailsWith<ConflitoException> {
            service.enviar(jpeg, FotoService.EnvioFoto(diarioId = d.id.toString(), tipo = "livre"))
        }
        assertEquals("DIARIO_ASSINADO", erro.codigo)
        naoGravou()
    }

    @Test
    fun `arquivo que nao e JPEG nem PNG responde 415 mesmo com nome jpg`() {
        val d = diario()
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)
        val erro = assertFailsWith<ApiException> {
            service.enviar(pdfComNomeDeJpg, FotoService.EnvioFoto(diarioId = d.id.toString(), tipo = "livre"))
        }
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, erro.status)
        assertEquals("TIPO_ARQUIVO_INVALIDO", erro.codigo)
    }

    @Test
    fun `tipo de foto desconhecido e vinculo errado respondem 400`() {
        assertEquals("TIPO_FOTO_INVALIDO", assertFailsWith<RegraNegocioException> {
            service.enviar(jpeg, FotoService.EnvioFoto(diarioId = UUID.randomUUID().toString(), tipo = "selfie"))
        }.codigo)
        assertEquals("VINCULO_INVALIDO", assertFailsWith<RegraNegocioException> {
            service.enviar(jpeg, FotoService.EnvioFoto(tipo = "cupom_fiscal"))   // sem abastecimentoId
        }.codigo)
    }

    @Test
    fun `cupom do comboio pode ser enviado por quem registrou o abastecimento`() {
        comoMotorista("carlos")   // motorista do comboio
        val diarioDoAbastecido = diario(dono = "ana")
        val abastecimento = UUID.randomUUID()
        given(fotoRepository.abastecimentoDaFoto(abastecimento)).willReturn(FotoRepository.AbastecimentoDaFoto(diarioDoAbastecido.id, "carlos"))
        given(diarioDoEvento.porId(diarioDoAbastecido.id.toString())).willReturn(diarioDoAbastecido)

        val r = service.enviar(jpeg, FotoService.EnvioFoto(tipo = "cupom_fiscal", abastecimentoId = abastecimento.toString()))

        assertEquals(diarioDoAbastecido.id, r.diarioId)
    }

    @Test
    fun `cupom de abastecimento de outros motoristas responde 403`() {
        comoMotorista("beto")
        val d = diario(dono = "ana")
        val abastecimento = UUID.randomUUID()
        given(fotoRepository.abastecimentoDaFoto(abastecimento)).willReturn(FotoRepository.AbastecimentoDaFoto(d.id, "carlos"))
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)
        assertFailsWith<ProibidoException> {
            service.enviar(jpeg, FotoService.EnvioFoto(tipo = "bomba_comboio", abastecimentoId = abastecimento.toString()))
        }
        naoGravou()
    }

    // ===================== Etapa 3: leitura =====================

    private fun fotoDe(diarioId: UUID, registradoPor: String?, id: UUID = UUID.randomUUID()) = FotoRepository.FotoMetadados(
        id, diarioId, "cupom_fiscal", null, null, null, null, 7, "image/jpeg", "abc", null, false, null, registradoPor
    )

    private fun fotoGuardada(foto: FotoRepository.FotoMetadados, dono: String) {
        given(fotoRepository.buscarMetadados(foto.id)).willReturn(foto)
        given(fotoRepository.lerConteudo(foto.id)).willReturn(FotoRepository.ConteudoGuardado(jpeg.bytes, null))
        given(diarioDoEvento.porIdSeExistir(foto.diarioId.toString())).willReturn(diario(dono = dono).copy(id = foto.diarioId))
    }

    @Test
    fun `dono do diario e quem enviou no comboio leem a imagem`() {
        val foto = fotoDe(UUID.randomUUID(), registradoPor = "carlos")
        fotoGuardada(foto, dono = "ana")

        comoMotorista("ana")
        assertEquals("image/jpeg", service.lerImagem(foto.id.toString()).tipoMime)
        comoMotorista("carlos")
        assertEquals(7, service.lerImagem(foto.id.toString()).bytes.size)
    }

    @Test
    fun `motorista nao le foto de outro diario`() {
        val foto = fotoDe(UUID.randomUUID(), registradoPor = "ana")
        fotoGuardada(foto, dono = "ana")
        comoMotorista("beto")
        assertFailsWith<ProibidoException> { service.lerImagem(foto.id.toString()) }
    }

    @Test
    fun `foto inexistente responde 404 e id invalido 400`() {
        val erro = assertFailsWith<com.example.sistemaobras.exception.NaoEncontradoException> { service.lerImagem(UUID.randomUUID().toString()) }
        assertEquals("FOTO_NAO_ENCONTRADA", erro.codigo)
        assertFailsWith<RegraNegocioException> { service.lerImagem("abc") }
    }

    @Test
    fun `lista do diario confere o dono e nao traz bytes`() {
        val d = diario()
        given(diarioDoEvento.porId(d.id.toString())).willReturn(d)
        given(fotoRepository.listarDoDiario(d.id)).willReturn(listOf(fotoDe(d.id, "ana")))
        val lista = service.listarDoDiario(d.id.toString())
        assertEquals(1, lista.size)
        verify(diarioDoEvento).conferirDono(d)
    }

    @Test
    fun `consulta de metadados nunca seleciona o conteudo da foto`() {
        kotlin.test.assertFalse(FotoRepository.METADADOS.contains("conteudo"))
    }

    @Test
    fun `uso mostra bytes e megabytes`() {
        given(fotoRepository.uso()).willReturn(FotoRepository.Uso(3, 3L * 1024 * 1024, 4L * 1024 * 1024, 20L * 1024 * 1024))
        val u = service.uso()
        assertEquals(3, u.quantidadeFotos)
        assertEquals(3.0, u.megabytesFotos)
        assertEquals(20.0, u.megabytesBanco)
    }

    @Test
    fun `sem vaga para gravar responde 503 FOTO_OCUPADO`() {
        val semVagas = FotoService(fotoRepository, diarioDoEvento, ArmazenamentoFotosNoBanco(), enviosSimultaneos = 0)
        val erro = assertFailsWith<ApiException> {
            semVagas.enviar(jpeg, FotoService.EnvioFoto(diarioId = UUID.randomUUID().toString(), tipo = "livre"))
        }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, erro.status)
        assertEquals("FOTO_OCUPADO", erro.codigo)
    }
}
