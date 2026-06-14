package com.example.sistemaobras.service

import com.example.sistemaobras.dto.LogResponse
import com.example.sistemaobras.entity.Log
import com.example.sistemaobras.repository.LogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class LogService(
    private val logRepository: LogRepository
) {

    fun registrar(
        usuarioLogin: String,
        usuarioNome: String?,
        acao: String,
        descricao: String,
        ip: String? = null
    ) {
        logRepository.save(Log(
            usuarioLogin = usuarioLogin,
            usuarioNome = usuarioNome,
            acao = acao,
            descricao = descricao,
            ip = ip
        ))
    }

    fun listarTodos(): List<LogResponse> {
        return logRepository.findAll()
            .sortedByDescending { it.criadoEm }
            .map { toResponse(it) }
    }

    fun filtrar(
        login: String?,
        acao: String?,
        inicio: String?,
        fim: String?
    ): List<LogResponse> {
        val inicioDate = inicio?.let { LocalDateTime.parse(it + "T00:00:00") }
        val fimDate = fim?.let { LocalDateTime.parse(it + "T23:59:59") }
        return logRepository.filtrar(login, acao, inicioDate, fimDate)
            .map { toResponse(it) }
    }

    private fun toResponse(l: Log) = LogResponse(
        id = l.id,
        usuarioLogin = l.usuarioLogin,
        usuarioNome = l.usuarioNome,
        acao = l.acao,
        descricao = l.descricao,
        ip = l.ip,
        criadoEm = l.criadoEm
    )
}