package com.example.sistemaobras.service

import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.ProibidoException
import com.example.sistemaobras.exception.uuidValido
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.security.ControleAcesso
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * Parte 6B: acha e valida o diário ao qual um evento (abastecimento, ocorrência,
 * ponto de rastreamento, checklist) pertence. Com a fila offline o evento pode
 * chegar depois do fechamento, então o diário não precisa estar aberto AGORA.
 */
@Service
class DiarioDoEvento(private val diarioRepository: DiarioBordoRepository) {

    data class Diario(
        val id: UUID,
        val dono: String,
        val status: String,
        val abertoEm: LocalDateTime,
        val fechadoEm: LocalDateTime?,
        val veiculoId: UUID,
        val idLocalAbertura: UUID?,
        val idLocalFechamento: UUID?,
        val medidorInicial: Double
    ) {
        val assinado: Boolean get() = status == "assinado"
        val aberto: Boolean get() = status == "aberto"
    }

    fun porId(id: String): Diario =
        diarioRepository.buscarParaEvento(uuidValido(id).toString()).firstOrNull()?.let(::linha)
            ?: throw NaoEncontradoException("Diário não encontrado.", CodigosErro.DIARIO_NAO_ENCONTRADO)

    // Para as consultas por id de diário: null se o id for inválido ou não existir.
    fun porIdSeExistir(id: String): Diario? =
        runCatching { UUID.fromString(id) }.getOrNull()
            ?.let { diarioRepository.buscarParaEvento(it.toString()).firstOrNull()?.let(::linha) }

    fun porIdLocalAbertura(idLocal: UUID): Diario? =
        diarioRepository.buscarPorIdLocalAbertura(idLocal.toString()).firstOrNull()?.let(::linha)

    fun porIdLocalFechamento(idLocal: UUID): Diario? =
        diarioRepository.buscarPorIdLocalFechamento(idLocal.toString()).firstOrNull()?.let(::linha)

    fun porVeiculoNoHorario(veiculoId: String, instante: LocalDateTime): Diario? =
        diarioRepository.buscarPorVeiculoNoHorario(uuidValido(veiculoId).toString(), instante).firstOrNull()?.let(::linha)

    fun motoristaDirigiaComboio(login: String, instante: LocalDateTime): Boolean =
        diarioRepository.contarDiarioComboioNoHorario(login, instante) > 0

    // Motorista só registra/vê eventos do próprio diário (gestor e chamada sem token passam).
    fun conferirDono(diario: Diario) {
        if (ControleAcesso.ehMotorista() && ControleAcesso.loginDoToken() != diario.dono)
            throw ProibidoException("Este diário é de outro motorista.", CodigosErro.DIARIO_DE_OUTRO_MOTORISTA)
    }

    // Diário assinado é documento fechado: não recebe evento novo.
    fun conferirNaoAssinado(diario: Diario) {
        if (diario.assinado)
            throw ConflitoException("Este diário já foi assinado e não aceita novos registros.", CodigosErro.DIARIO_ASSINADO)
    }

    private fun linha(r: Array<Any?>) = Diario(
        id = UUID.fromString(r[0].toString()),
        dono = r[1].toString(),
        status = r[2].toString(),
        abertoEm = dataHora(r[3])!!,
        fechadoEm = dataHora(r[4]),
        veiculoId = UUID.fromString(r[5].toString()),
        idLocalAbertura = r[6]?.let { UUID.fromString(it.toString()) },
        idLocalFechamento = r[7]?.let { UUID.fromString(it.toString()) },
        medidorInicial = r[8].toString().toDouble()
    )

    private fun dataHora(x: Any?): LocalDateTime? = when (x) {
        null -> null
        is LocalDateTime -> x
        is java.sql.Timestamp -> x.toLocalDateTime()
        else -> LocalDateTime.parse(x.toString().replace(" ", "T"))
    }
}
