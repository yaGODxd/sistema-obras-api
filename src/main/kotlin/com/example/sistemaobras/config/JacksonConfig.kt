package com.example.sistemaobras.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Parte 6C: datas com fuso nas respostas.
 * As datas do banco já são UTC (6A), mas saem sem fuso ("2026-10-19T12:00:00").
 * Com APP_DATAS_COM_FUSO=true saem com Z ("2026-10-19T12:00:00Z").
 * Padrão DESLIGADO: só ligar depois que o app ler datas com Instant/OffsetDateTime
 * (LocalDateTime.parse quebra com o Z). O front já aceita os dois formatos.
 */
@Configuration
class JacksonConfig {

    @Bean
    fun moduloDatasComFuso(@Value("\${app.datas-com-fuso:false}") comFuso: Boolean): JacksonModule =
        criarModulo(comFuso)

    companion object {
        fun criarModulo(comFuso: Boolean): JacksonModule {
            val modulo = SimpleModule("DatasComFuso")
            if (comFuso) modulo.addSerializer(LocalDateTime::class.java, DataUtcComZ())
            return modulo
        }
    }

    private class DataUtcComZ : StdSerializer<LocalDateTime>(LocalDateTime::class.java) {
        override fun serialize(valor: LocalDateTime, gerador: JsonGenerator, contexto: SerializationContext) {
            gerador.writeString(valor.toInstant(ZoneOffset.UTC).toString())
        }
    }
}
