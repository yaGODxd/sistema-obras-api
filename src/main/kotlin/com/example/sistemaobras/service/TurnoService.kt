package com.example.sistemaobras.service

import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.exception.uuidValido
import com.example.sistemaobras.dto.TurnoRequest
import com.example.sistemaobras.dto.TurnoResponse
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.repository.TurnoRepository
import com.example.sistemaobras.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class TurnoService(
    private val turnoRepository: TurnoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val diarioRepository: DiarioBordoRepository,
    // APP_BLOQUEIOS_DIARIO=false desliga o bloqueio do item 15.
    @Value("\${app.bloqueios.diario:true}") private val bloqueiosAtivos: Boolean = true
) {

    // Turno lido do banco (colunas de COLUNAS_TURNO).
    private data class TurnoLido(val id: UUID, val login: String, val status: String, val abertoEm: LocalDateTime?)

    private fun ler(r: Array<Any?>) = TurnoLido(
        id = UUID.fromString(r[0].toString()),
        login = r[1].toString(),
        status = r[2].toString(),
        abertoEm = r[3]?.let { LocalDateTime.parse(it.toString().replace(" ", "T")) }
    )

    // Reenvio com o mesmo idLocal: devolve o turno já gravado, se for do mesmo motorista.
    private fun jaRecebido(linhas: List<Array<Any?>>, login: String): TurnoResponse? {
        val turno = linhas.firstOrNull()?.let(::ler) ?: return null
        if (turno.login != login)
            throw ConflitoException("Este idLocal já foi usado em outro registro.", CodigosErro.ID_LOCAL_EM_USO)
        return TurnoResponse(id = turno.id, login = turno.login, status = turno.status, abertoEm = turno.abertoEm)
    }

    @Transactional
    fun abrirTurno(request: TurnoRequest): TurnoResponse {
        val login = request.login
        val idLocal = request.idLocal?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        idLocal?.let { jaRecebido(turnoRepository.buscarPorIdLocalAbertura(it.toString()), login) }?.let { return it }

        usuarioRepository.findByLogin(login)
            ?: throw NaoEncontradoException("Usuário não encontrado.")

        if (turnoRepository.temTurnoAberto(login) > 0)
            throw RegraNegocioException("Já existe um turno aberto para este usuário.")

        val horario = HorarioEvento.resolver(request.ocorridoEm, null, request.enviadoEm)
        turnoRepository.abrirTurno(login, horario.ocorrido, horario.recebido, idLocal?.toString(), horario.suspeito, horario.motivoTexto)

        val aberto = turnoRepository.buscarTurnoAberto(login).firstOrNull()?.let(::ler)
        return TurnoResponse(id = aberto?.id, login = login, status = "aberto", abertoEm = horario.ocorrido)
    }

    @Transactional
    fun fecharTurno(request: TurnoRequest): TurnoResponse {
        val login = request.login
        val idLocal = request.idLocal?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        idLocal?.let { jaRecebido(turnoRepository.buscarPorIdLocalFechamento(it.toString()), login) }?.let { return it }

        val turno = turnoRepository.buscarTurnoAberto(login).firstOrNull()?.let(::ler)
            ?: throw RegraNegocioException("Não há turno aberto para este usuário.")

        // Item 15: só o diário ABERTO impede sair de serviço. Diário fechado
        // pendente de assinatura não impede (ele bloqueia a abertura do próximo).
        if (bloqueiosAtivos && diarioRepository.temDiarioAberto(login) > 0)
            throw RegraNegocioException(
                "Você tem um diário aberto. Feche o diário antes de sair de serviço.",
                CodigosErro.TURNO_COM_DIARIO_ABERTO
            )

        var horario = HorarioEvento.resolver(request.ocorridoEm, null, request.enviadoEm)
        turno.abertoEm?.let { horario = horario.conferirJanela(it, null, CodigosErro.FORA_DA_JANELA_TURNO) }
        turnoRepository.fecharTurno(login, horario.ocorrido, horario.recebido, idLocal?.toString(), horario.suspeito, horario.motivoTexto)

        return TurnoResponse(id = turno.id, login = login, status = "fechado", abertoEm = turno.abertoEm)
    }

    fun temTurnoAberto(login: String): Boolean {
        return turnoRepository.temTurnoAberto(login) > 0
    }
}
