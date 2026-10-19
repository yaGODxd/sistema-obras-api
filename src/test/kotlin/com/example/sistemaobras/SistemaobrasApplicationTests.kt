package com.example.sistemaobras

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

// Desligado: sobe a aplicação inteira e conecta no banco das variáveis de
// ambiente (que pode ser o de produção). Os testes usam @WebMvcTest e mocks.
@Disabled("Sobe o contexto completo e conecta ao banco real")
@SpringBootTest
class SistemaobrasApplicationTests {

    @Test
    fun contextLoads() {
    }

}
