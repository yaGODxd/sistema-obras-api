package com.example.sistemaobras.service

import com.example.sistemaobras.dto.DefinirPinRequest
import com.example.sistemaobras.dto.DiarioDetalheResponse
import com.example.sistemaobras.dto.EditarDiarioRequest
import com.example.sistemaobras.dto.VerificacaoDiarioResponse
import com.example.sistemaobras.repository.AssinaturaRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

class AssinaturaException(val httpStatus: Int, mensagem: String) : RuntimeException(mensagem)

@Service
class AssinaturaService(
    private val repo: AssinaturaRepository,
    private val logService: LogService
) {
    private val bcrypt = BCryptPasswordEncoder()

    companion object {
        const val MAX_TENTATIVAS = 5
        const val BLOQUEIO_MINUTOS = 15 // igual ao INTERVAL de AssinaturaRepository.bloquearPin
        private val PIN_VALIDO = Regex("^\\d{4,6}$")
    }

    // ===================== PIN =====================

    fun pinDefinido(login: String): Boolean = repo.contarPinDefinido(login) > 0

    fun definirPin(login: String, req: DefinirPinRequest) {
        val dados = repo.dadosPin(login).firstOrNull()
            ?: throw AssinaturaException(404, "Usuário não encontrado")

        if (!bcrypt.matches(req.senha, dados[0].toString()))
            throw AssinaturaException(403, "Senha incorreta")

        if (!PIN_VALIDO.matches(req.novoPin))
            throw AssinaturaException(400, "O PIN deve ter de 4 a 6 dígitos numéricos")

        repo.definirPin(login, bcrypt.encode(req.novoPin)!!)

        logService.registrar(login, null, "DEFINIR_PIN", "PIN de assinatura cadastrado ou alterado")
    }

    private fun conferirPin(login: String, pin: String) {
        val dados = repo.dadosPin(login).firstOrNull()
            ?: throw AssinaturaException(404, "Usuário não encontrado")

        val pinHash = dados[1]?.toString()
            ?: throw AssinaturaException(400, "Cadastre seu PIN de assinatura antes de assinar")

        val bloqueado = dados[2] as? Boolean ?: false
        if (bloqueado) {
            val minutos = dados[3]?.toString()?.toDoubleOrNull()?.toInt() ?: BLOQUEIO_MINUTOS
            throw AssinaturaException(
                423, "PIN bloqueado por excesso de tentativas. Tente novamente em cerca de $minutos min."
            )
        }

        if (bcrypt.matches(pin, pinHash)) {
            repo.zerarTentativasPin(login)
            return
        }

        repo.incrementarTentativasPin(login)
        val tentativas = repo.tentativasPin(login) ?: 0
        if (tentativas >= MAX_TENTATIVAS) {
            repo.bloquearPin(login)
            throw AssinaturaException(
                423, "PIN incorreto. Muitas tentativas: bloqueado por $BLOQUEIO_MINUTOS minutos."
            )
        }
        throw AssinaturaException(403, "PIN incorreto. Restam ${MAX_TENTATIVAS - tentativas} tentativa(s).")
    }

    // ===================== DIÁRIOS =====================

    fun historico(login: String, mes: Int?, ano: Int?, status: String?): List<DiarioDetalheResponse> =
        repo.historicoDoMotorista(login, mes, ano, status?.takeIf { it.isNotBlank() })
            .map { toResponse(it) }

    fun editar(login: String, diarioId: String, req: EditarDiarioRequest): DiarioDetalheResponse {
        val antes = linha(diarioId)
        if (antes[3].toString() != login)
            throw AssinaturaException(403, "Este diário não pertence a você")
        if (antes[12].toString() != "fechado")
            throw AssinaturaException(400, "Só é possível editar diário fechado e ainda não assinado")

        val destino = req.destino?.trim()
        val observacao = req.observacaoFechamento?.trim()
        val motivo = req.motivoUsoId?.trim()?.takeIf { it.isNotEmpty() }

        if (destino == null && observacao == null && motivo == null)
            throw AssinaturaException(400, "Informe ao menos um campo para editar")

        if (motivo != null) {
            try {
                UUID.fromString(motivo)
            } catch (e: IllegalArgumentException) {
                throw AssinaturaException(400, "motivoUsoId inválido")
            }
        }

        val alterados = repo.editarDiarioFechado(diarioId, destino, motivo, observacao)
        if (alterados == 0)
            throw AssinaturaException(409, "O diário não pode mais ser editado (já foi assinado?)")

        val mudancas = mutableListOf<String>()
        if (destino != null && destino != (antes[9]?.toString() ?: ""))
            mudancas += "destino: '${cortar(antes[9]?.toString())}' → '${cortar(destino)}'"
        if (observacao != null && observacao != (antes[11]?.toString() ?: ""))
            mudancas += "observação: '${cortar(antes[11]?.toString())}' → '${cortar(observacao)}'"
        if (motivo != null && motivo != (antes[10]?.toString() ?: ""))
            mudancas += "motivo de uso alterado"

        logService.registrar(
            login, antes[4]?.toString(), "EDITAR_DIARIO",
            "Diário editado antes da assinatura" +
                    if (mudancas.isNotEmpty()) " — " + mudancas.joinToString("; ") else ""
        )

        return toResponse(linha(diarioId))
    }

    fun assinar(login: String, diarioId: String, pin: String): DiarioDetalheResponse {
        val r = linha(diarioId)
        if (r[3].toString() != login)
            throw AssinaturaException(403, "Este diário não pertence a você")
        if (r[12].toString() != "fechado")
            throw AssinaturaException(400, "O diário não está pendente de assinatura (status atual: ${r[12]})")

        conferirPin(login, pin)

        val hash = calcularHash(r, repo.abastecimentosDoDiario(diarioId))
        val alterados = repo.assinarDiario(diarioId, hash)
        if (alterados == 0)
            throw AssinaturaException(409, "O diário foi alterado durante a assinatura. Tente novamente.")

        logService.registrar(
            login, r[4]?.toString(), "ASSINAR_DIARIO",
            "Diário assinado — resumo ${hash.take(12)}…"
        )

        return toResponse(linha(diarioId))
    }

    fun verificar(solicitante: String, ehGestor: Boolean, diarioId: String): VerificacaoDiarioResponse {
        val r = linha(diarioId)
        if (!ehGestor && r[3].toString() != solicitante)
            throw AssinaturaException(403, "Sem permissão para verificar este diário")

        val hashSalvo = r[16]?.toString()
        if (r[12].toString() != "assinado" || hashSalvo == null) {
            return VerificacaoDiarioResponse(
                assinado = false, integro = false, assinadoEm = null,
                mensagem = "Este diário ainda não foi assinado."
            )
        }

        val hashAtual = calcularHash(r, repo.abastecimentosDoDiario(diarioId))
        val integro = hashAtual == hashSalvo
        return VerificacaoDiarioResponse(
            assinado = true,
            integro = integro,
            assinadoEm = dataHora(r[15]),
            mensagem = if (integro)
                "Conteúdo íntegro: nada foi alterado depois da assinatura."
            else
                "ATENÇÃO: o conteúdo do diário é diferente do que foi assinado."
        )
    }

    // ===================== AUXILIARES =====================

    private fun linha(diarioId: String): Array<Any?> {
        try {
            UUID.fromString(diarioId)
        } catch (e: IllegalArgumentException) {
            throw AssinaturaException(400, "ID de diário inválido")
        }
        return repo.buscarDiario(diarioId).firstOrNull()
            ?: throw AssinaturaException(404, "Diário não encontrado")
    }

    // Resumo SHA-256 do conteúdo do diário e dos abastecimentos dele.
    // Qualquer alteração depois da assinatura muda o resultado.
    private fun calcularHash(r: Array<Any?>, abastecimentos: List<Array<Any?>>): String {
        val sb = StringBuilder()
        sb.append("diario=").append(norm(r[0])).append('\n')
        sb.append("motorista=").append(norm(r[3])).append('\n')
        sb.append("veiculo=").append(norm(r[5])).append('\n')
        sb.append("medidor_inicial=").append(norm(r[6])).append('\n')
        sb.append("medidor_final=").append(norm(r[7])).append('\n')
        sb.append("destino=").append(norm(r[9])).append('\n')
        sb.append("motivo_uso=").append(norm(r[10])).append('\n')
        sb.append("observacao=").append(norm(r[11])).append('\n')
        sb.append("aberto_em=").append(norm(r[13])).append('\n')
        sb.append("fechado_em=").append(norm(r[14])).append('\n')
        abastecimentos.forEach { a ->
            sb.append("abastecimento=").append(a.joinToString("|") { norm(it) }).append('\n')
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(sb.toString().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun norm(x: Any?): String = when (x) {
        null -> ""
        is Number -> try {
            BigDecimal(x.toString()).stripTrailingZeros().toPlainString()
        } catch (e: NumberFormatException) {
            x.toString()
        }
        is java.sql.Timestamp -> x.toLocalDateTime().toString()
        else -> x.toString()
    }

    private fun dataHora(x: Any?): LocalDateTime? = when (x) {
        null -> null
        is java.sql.Timestamp -> x.toLocalDateTime()
        is LocalDateTime -> x
        else -> try {
            LocalDateTime.parse(x.toString().replace(" ", "T"))
        } catch (e: Exception) {
            null
        }
    }

    private fun cortar(s: String?): String {
        val t = s ?: ""
        return if (t.length > 60) t.take(60) + "…" else t
    }

    private fun toResponse(r: Array<Any?>) = DiarioDetalheResponse(
        id = UUID.fromString(r[0].toString()),
        veiculoDescricao = r[1]?.toString() ?: "",
        veiculoPlaca = r[2]?.toString(),
        motoristaLogin = r[3]?.toString(),
        motoristaNome = r[4]?.toString() ?: "",
        medidorInicial = r[6].toString().toDouble(),
        medidorFinal = r[7]?.toString()?.toDoubleOrNull(),
        medidorPercorrido = r[8]?.toString()?.toDoubleOrNull(),
        destino = r[9]?.toString(),
        motivoUsoId = r[10]?.toString(),
        observacaoFechamento = r[11]?.toString(),
        status = r[12]?.toString() ?: "",
        abertoEm = dataHora(r[13]),
        fechadoEm = dataHora(r[14]),
        assinadoEm = dataHora(r[15])
    )
}