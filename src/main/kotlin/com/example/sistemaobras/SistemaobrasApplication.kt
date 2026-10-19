package com.example.sistemaobras

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
class SistemaobrasApplication

fun main(args: Array<String>) {
    // Horários do servidor sempre em UTC, em qualquer máquina (Render ou local).
    // O banco guarda UTC; o fuso de Brasília é só para exibição e cortes de dia/mês.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    runApplication<SistemaobrasApplication>(*args)
}
