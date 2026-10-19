package com.example.sistemaobras.service

import com.example.sistemaobras.dto.LogResponse
import com.example.sistemaobras.entity.Log
import com.example.sistemaobras.repository.LogRepository
import com.example.sistemaobras.repository.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
class LogService(
    private val logRepository: LogRepository,
    private val usuarioRepository: UsuarioRepository,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(LogService::class.java)

    // O log grava em transação PRÓPRIA (REQUIRES_NEW). Uma falha ao gravar o log
    // é registrada no log do servidor e não desfaz a operação principal.
    private val transacaoPropria = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun registrar(
        usuarioLogin: String,
        usuarioNome: String?,
        acao: String,
        descricao: String,
        ip: String? = null
    ) {
        try {
            transacaoPropria.executeWithoutResult {
                // As colunas de logs são varchar(255). A descrição junta textos digitados
                // (ex.: descrição da ocorrência) e pode passar disso: corta em vez de falhar.
                logRepository.save(Log(
                    usuarioLogin = cortar(usuarioLogin),
                    usuarioNome = usuarioNome?.let { cortar(it) },
                    acao = cortar(acao),
                    descricao = cortar(descricao),
                    ip = ip?.let { cortar(it) }
                ))
            }
        } catch (e: Exception) {
            log.error("Falha ao gravar o log {} de {} (a operação principal seguiu)", acao, usuarioLogin, e)
        }
    }

    // Login de quem fez a requisição, se ela veio com token válido; senão null.
    fun loginAutenticado(): String? {
        val autenticacao = SecurityContextHolder.getContext().authentication
        if (autenticacao == null || !autenticacao.isAuthenticated || autenticacao is AnonymousAuthenticationToken) return null
        return autenticacao.name
    }

    private fun cortar(texto: String): String =
        if (texto.length <= TAMANHO_COLUNA) texto else texto.take(TAMANHO_COLUNA - 1) + "…"

    companion object {
        const val TAMANHO_COLUNA = 255
    }

    fun listarTodos(): List<LogResponse> {
        return comFotos(logRepository.findAll().sortedByDescending { it.criadoEm })
    }

    fun filtrar(
        login: String?,
        acao: String?,
        inicio: String?,
        fim: String?
    ): List<LogResponse> {
        val inicioDate = inicio?.let { LocalDateTime.parse(it + "T00:00:00") }
        val fimDate = fim?.let { LocalDateTime.parse(it + "T23:59:59") }
        return comFotos(logRepository.filtrar(login, acao, inicioDate, fimDate))
    }

    // As fotos dos autores vêm numa consulta só (antes era uma por linha de log).
    private fun comFotos(logs: List<Log>): List<LogResponse> {
        val logins = logs.map { it.usuarioLogin }.distinct()
        val fotos = if (logins.isEmpty()) emptyMap() else try {
            usuarioRepository.findFotosByLogins(logins).associate { it[0].toString() to it[1]?.toString() }
        } catch (e: Exception) {
            log.warn("Não foi possível carregar as fotos dos logs: {}", e.message)
            emptyMap()
        }
        return logs.map { toResponse(it, fotos[it.usuarioLogin]) }
    }

    private fun toResponse(l: Log, foto: String?) = LogResponse(
        id = l.id,
        usuarioLogin = l.usuarioLogin,
        usuarioNome = l.usuarioNome,
        usuarioFoto = foto,
        acao = l.acao,
        descricao = l.descricao,
        ip = l.ip,
        criadoEm = l.criadoEm
    )
}