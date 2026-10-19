package com.example.sistemaobras.service

import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.dto.TurnoResponse
import com.example.sistemaobras.repository.TurnoRepository
import com.example.sistemaobras.repository.UsuarioRepository
import org.springframework.stereotype.Service

@Service
class TurnoService(
    private val turnoRepository: TurnoRepository,
    private val usuarioRepository: UsuarioRepository
) {

    fun abrirTurno(login: String): TurnoResponse {
        val usuario = usuarioRepository.findByLogin(login)
            ?: throw NaoEncontradoException("Usuário não encontrado.")

        if (turnoRepository.temTurnoAberto(login) > 0)
            throw RegraNegocioException("Já existe um turno aberto para este usuário.")

        turnoRepository.abrirTurno(login)

        return TurnoResponse(
            id = null,
            login = login,
            status = "aberto",
            abertoEm = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
        )
    }

    fun fecharTurno(login: String): TurnoResponse {
        if (turnoRepository.temTurnoAberto(login) == 0L)
            throw RegraNegocioException("Não há turno aberto para este usuário.")

        turnoRepository.fecharTurno(login)

        return TurnoResponse(
            id = null,
            login = login,
            status = "fechado",
            abertoEm = null
        )
    }

    fun temTurnoAberto(login: String): Boolean {
        return turnoRepository.temTurnoAberto(login) > 0
    }
}