package com.example.sistemaobras.service

import com.example.sistemaobras.entity.Log
import com.example.sistemaobras.repository.LogRepository
import com.example.sistemaobras.repository.UsuarioRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogServiceTest {

    private val logRepository = mock(LogRepository::class.java)
    private val transactionManager = mock(PlatformTransactionManager::class.java).also {
        given(it.getTransaction(Mockito.any())).willReturn(SimpleTransactionStatus())
    }
    private val service = LogService(logRepository, mock(UsuarioRepository::class.java), transactionManager)

    private fun logGravado(): Log {
        val captor = ArgumentCaptor.forClass(Log::class.java)
        verify(logRepository).save(captor.capture())
        return captor.value
    }

    @Test
    fun `descricao maior que a coluna e cortada em vez de falhar`() {
        service.registrar("motorista", null, "OCORRENCIA", "Ocorrência registrada — " + "x".repeat(300))
        val log = logGravado()
        assertEquals(255, log.descricao.length)
        assertTrue(log.descricao.endsWith("…"))
    }

    @Test
    fun `descricao curta fica igual`() {
        service.registrar("motorista", null, "LOGIN", "Usuário realizou login no sistema")
        assertEquals("Usuário realizou login no sistema", logGravado().descricao)
    }

    @Test
    fun `falha ao gravar o log nao derruba a operacao principal`() {
        given(logRepository.save(Mockito.any(Log::class.java))).willThrow(IllegalStateException("banco fora"))
        service.registrar("motorista", null, "LOGIN", "x")   // não lança
        verify(transactionManager).rollback(Mockito.any())
    }

    @Test
    fun `filtro de logs usa o dia de Brasilia`() {
        service.filtrar(null, null, "2026-10-19", "2026-10-19")
        // 19/10 em Brasília (UTC-3) = de 19/10 03:00 até 20/10 02:59:59.999999 em UTC
        verify(logRepository).filtrar(
            null, null,
            java.time.LocalDateTime.of(2026, 10, 19, 3, 0),
            java.time.LocalDateTime.of(2026, 10, 20, 2, 59, 59, 999_999_000)
        )
    }

    @Test
    fun `sem token o autor do log e nulo`() {
        assertEquals(null, service.loginAutenticado())
    }
}
