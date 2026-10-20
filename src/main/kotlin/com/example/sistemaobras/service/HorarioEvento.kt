package com.example.sistemaobras.service

import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.NaoProcessavelException
import com.example.sistemaobras.exception.RegraNegocioException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Horário de um evento vindo do celular (Parte 6B). Regras do coordenador (19/10):
 *  - vale ocorridoEm (ISO-8601 com fuso); senão registradoEm (epoch ms); senão a hora do servidor;
 *  - até 24 h no futuro: aceita e marca HORARIO_FUTURO; mais que isso: recusa 422 HORARIO_FUTURO;
 *  - mais de 7 dias atrás: aceita e marca HORARIO_ANTIGO;
 *  - enviadoEm: desvio do relógio do aparelho; mais de 5 min: marca DESVIO_RELOGIO (não corrige).
 * Tudo em UTC (LocalDateTime sem fuso, como as colunas do banco).
 */
data class HorarioEvento(
    val ocorrido: LocalDateTime,
    val recebido: LocalDateTime,
    val desvioSeg: Int?,
    val motivos: List<String>
) {
    val suspeito: Boolean get() = motivos.isNotEmpty()

    // Texto para a coluna motivo_suspeita (varchar 255); null quando não há suspeita.
    val motivoTexto: String? get() = motivos.distinct().joinToString(",").takeIf { it.isNotEmpty() }

    fun comMotivo(motivo: String) = copy(motivos = motivos + motivo)

    // Fora do período [inicio, fim] (fim nulo = até agora), com a mesma tolerância de 5 min.
    fun conferirJanela(inicio: LocalDateTime, fim: LocalDateTime?, motivo: String): HorarioEvento {
        val limiteFim = (fim ?: recebido).plus(TOLERANCIA)
        val fora = ocorrido.isBefore(inicio.minus(TOLERANCIA)) || ocorrido.isAfter(limiteFim)
        return if (fora) comMotivo(motivo) else this
    }

    companion object {
        val TOLERANCIA: Duration = Duration.ofMinutes(5)
        val FUTURO_MAXIMO: Duration = Duration.ofHours(24)
        val ANTIGO_DEMAIS: Duration = Duration.ofDays(7)

        fun agoraUtc(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

        fun resolver(
            ocorridoEm: String?,
            registradoEmMs: Long? = null,
            enviadoEm: String? = null,
            agora: LocalDateTime = agoraUtc()
        ): HorarioEvento {
            val ocorrido = when {
                !ocorridoEm.isNullOrBlank() -> lerInstante(ocorridoEm, "ocorridoEm")
                registradoEmMs != null -> LocalDateTime.ofInstant(Instant.ofEpochMilli(registradoEmMs), ZoneOffset.UTC)
                else -> agora
            }
            val motivos = mutableListOf<String>()

            val adiantado = Duration.between(agora, ocorrido)
            if (adiantado > FUTURO_MAXIMO)
                throw NaoProcessavelException(
                    "O horário do registro está mais de 24 horas no futuro. Confira a data e a hora do celular.",
                    CodigosErro.HORARIO_FUTURO
                )
            if (adiantado > TOLERANCIA) motivos += CodigosErro.HORARIO_FUTURO
            if (Duration.between(ocorrido, agora) > ANTIGO_DEMAIS) motivos += CodigosErro.HORARIO_ANTIGO

            val desvio = enviadoEm?.takeIf { it.isNotBlank() }?.let {
                Duration.between(lerInstante(it, "enviadoEm"), agora).seconds.toInt()
            }
            if (desvio != null && Math.abs(desvio.toLong()) > TOLERANCIA.seconds) motivos += CodigosErro.DESVIO_RELOGIO

            return HorarioEvento(ocorrido, agora, desvio, motivos)
        }

        // Exige fuso (Z ou ±hh:mm): sem fuso não dá para saber o instante real.
        private fun lerInstante(texto: String, campo: String): LocalDateTime =
            try {
                OffsetDateTime.parse(texto).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
            } catch (e: DateTimeParseException) {
                throw RegraNegocioException(
                    "Campo '$campo' inválido. Use data e hora com fuso, ex.: 2026-10-19T12:00:00Z.",
                    CodigosErro.HORARIO_INVALIDO
                )
            }
    }
}
