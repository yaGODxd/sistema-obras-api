package com.example.sistemaobras.config

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import kotlin.test.assertEquals

// Parte 6C: interruptor APP_DATAS_COM_FUSO.
class JacksonConfigTest {

    private val data = LocalDateTime.of(2026, 10, 19, 12, 0, 0)

    private fun json(comFuso: Boolean) =
        JsonMapper.builder().addModule(JacksonConfig.criarModulo(comFuso)).build().writeValueAsString(data)

    @Test
    fun `desligado as datas saem como antes, sem fuso`() {
        assertEquals("\"2026-10-19T12:00:00\"", json(false))
    }

    @Test
    fun `ligado as datas saem em UTC com Z`() {
        assertEquals("\"2026-10-19T12:00:00Z\"", json(true))
    }
}
