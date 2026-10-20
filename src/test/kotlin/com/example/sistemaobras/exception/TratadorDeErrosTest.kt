package com.example.sistemaobras.exception

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.*
import java.sql.SQLException
import java.util.UUID

// Testa o formato {"erro"} e a regra de status, sem segurança e sem banco.
@WebMvcTest(controllers = [TratadorDeErrosTest.RotasDeTeste::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(TratadorDeErrosTest.RotasDeTeste::class)
class TratadorDeErrosTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    data class Corpo(val nome: String, val valor: Double)

    @RestController
    @RequestMapping("/teste")
    class RotasDeTeste {
        @GetMapping("/nao-encontrado")
        fun naoEncontrado(): String = throw NaoEncontradoException("Veículo não encontrado.")

        @GetMapping("/conflito")
        fun conflito(): String = throw ConflitoException("Já existe.")

        @GetMapping("/fixo")
        @StatusErroFixo(400)
        fun fixo(): String = throw NaoEncontradoException("Usuário não encontrado.")

        @GetMapping("/fixo-inesperado")
        @StatusErroFixo(400)
        fun fixoInesperado(): String = throw IllegalStateException("detalhe interno")

        @GetMapping("/inesperado")
        fun inesperado(): String = throw IllegalStateException("detalhe interno")

        @GetMapping("/uuid/{id}")
        fun uuid(@PathVariable id: String): String = UUID.fromString(id).toString()

        @GetMapping("/duplicado")
        @StatusErroFixo(400)
        fun duplicado(): String = throw DataIntegrityViolationException(
            "falha",
            SQLException("duplicate key value violates unique constraint \"usuarios_login_key\"", "23505")
        )

        @GetMapping("/grande")
        fun grande(): String = throw org.springframework.web.multipart.MaxUploadSizeExceededException(5L * 1024 * 1024)

        @PostMapping("/corpo")
        fun corpo(@RequestBody corpo: Corpo): Corpo = corpo

        @GetMapping("/numero")
        fun numero(@RequestParam n: Int): Int = n
    }

    @Test
    fun `excecao de negocio usa o proprio status e a mensagem`() {
        mockMvc.perform(get("/teste/nao-encontrado"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.erro").value("Veículo não encontrado."))
        mockMvc.perform(get("/teste/conflito"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.erro").value("Já existe."))
    }

    @Test
    fun `rota com status fixo mantem o status antigo`() {
        mockMvc.perform(get("/teste/fixo"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Usuário não encontrado."))
        mockMvc.perform(get("/teste/fixo-inesperado"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Erro interno. Tente novamente."))
    }

    @Test
    fun `erro inesperado responde 500 sem detalhe tecnico`() {
        mockMvc.perform(get("/teste/inesperado"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.erro").value("Erro interno. Tente novamente."))
            .andExpect(content().string(not(containsString("detalhe interno"))))
    }

    @Test
    fun `uuid invalido tem mensagem propria`() {
        mockMvc.perform(get("/teste/uuid/abc"))
            .andExpect(jsonPath("$.erro").value("Identificador inválido."))
    }

    @Test
    fun `unicidade do banco identifica o login`() {
        mockMvc.perform(get("/teste/duplicado"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Já existe um usuário com este login."))
    }

    @Test
    fun `json invalido responde 400`() {
        mockMvc.perform(post("/teste/corpo").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Corpo da requisição inválido ou ausente."))
    }

    @Test
    fun `campo obrigatorio ausente responde 400 com o nome do campo`() {
        mockMvc.perform(post("/teste/corpo").contentType(MediaType.APPLICATION_JSON).content("""{"valor": 1}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value(containsString("nome")))
    }

    @Test
    fun `tipo errado no corpo responde 400 com o nome do campo`() {
        mockMvc.perform(post("/teste/corpo").contentType(MediaType.APPLICATION_JSON).content("""{"nome": "a", "valor": "abc"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value(containsString("valor")))
    }

    @Test
    fun `parametro com tipo errado ou ausente responde 400`() {
        mockMvc.perform(get("/teste/numero").param("n", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Valor inválido para 'n'."))
        mockMvc.perform(get("/teste/numero"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Parâmetro obrigatório ausente: n."))
    }

    @Test
    fun `foto acima do limite responde 413 com codigo`() {
        mockMvc.perform(get("/teste/grande"))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.erro").value("A foto passou do tamanho máximo permitido."))
            .andExpect(jsonPath("$.codigo").value("FOTO_GRANDE_DEMAIS"))
    }

    @Test
    fun `rota inexistente responde 404 no formato padrao`() {
        mockMvc.perform(get("/nao-existe"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.erro").value("Rota não encontrada."))
    }
}
