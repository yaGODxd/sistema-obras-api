package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.repository.*
import com.example.sistemaobras.security.JwtService
import com.example.sistemaobras.security.SecurityConfig
import com.example.sistemaobras.service.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

// Parte 5 com o interruptor LIGADO (APP_SEGURANCA_EXIGIR_JWT=true) e CORS restrito.
@WebMvcTest
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["app.seguranca.exigir-jwt=true", "app.cors.origens=http://localhost:4200"])
class Parte5SegurancaTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var jwtService: JwtService
    @MockitoBean lateinit var usuarioService: UsuarioService
    @MockitoBean lateinit var veiculoService: VeiculoService
    @MockitoBean lateinit var manutencaoService: ManutencaoService
    @MockitoBean lateinit var abastecimentoService: AbastecimentoService
    @MockitoBean lateinit var ocorrenciaService: OcorrenciaService
    @MockitoBean lateinit var rastreamentoService: RastreamentoService
    @MockitoBean lateinit var turnoService: TurnoService
    @MockitoBean lateinit var diarioBordoService: DiarioBordoService
    @MockitoBean lateinit var assinaturaService: AssinaturaService
    @MockitoBean lateinit var logService: LogService
    @MockitoBean lateinit var diarioDoEvento: DiarioDoEvento
    @MockitoBean lateinit var usuarioRepository: UsuarioRepository
    @MockitoBean lateinit var mecanicoRepository: MecanicoRepository
    @MockitoBean lateinit var postoRepository: PostoRepository
    @MockitoBean lateinit var secretariaRepository: SecretariaRepository
    @MockitoBean lateinit var checklistTemplateRepository: ChecklistTemplateRepository
    @MockitoBean lateinit var checklistItemRepository: ChecklistItemRepository
    @MockitoBean lateinit var checklistRespostaRepository: ChecklistRespostaRepository

    @BeforeEach
    fun tokens() {
        mapOf("tg" to ("gestor1" to "gestor"), "tm" to ("ana" to "motorista")).forEach { (token, quem) ->
            given(jwtService.validarToken(token)).willReturn(true)
            given(jwtService.extrairLogin(token)).willReturn(quem.first)
            given(jwtService.extrairPerfil(token)).willReturn(quem.second)
        }
    }

    // Mockito.any() devolve null; o cast evita a checagem de nulo do Kotlin.
    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        org.mockito.Mockito.any<T>()
        return null as T
    }

    private fun MockHttpServletRequestBuilder.token(t: String) = header("Authorization", "Bearer $t")
    private fun MockHttpServletRequestBuilder.json(corpo: String) = contentType(MediaType.APPLICATION_JSON).content(corpo)

    @Test
    fun `sem token so o login e publico`() {
        mockMvc.perform(get("/veiculos"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.erro").value("Não autenticado. Faça login novamente."))
        mockMvc.perform(post("/auth/login").json("""{"login":"a","senha":"b"}"""))
            .andExpect(status().isOk)
    }

    @Test
    fun `rota de gestor recusa motorista com 403`() {
        mockMvc.perform(get("/logs").token("tm"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.erro").value("Você não tem permissão para acessar este recurso."))
        mockMvc.perform(get("/logs").token("tg")).andExpect(status().isOk)
        mockMvc.perform(get("/diarios").token("tm")).andExpect(status().isForbidden)
        mockMvc.perform(get("/manutencoes").token("tm")).andExpect(status().isForbidden)
    }

    @Test
    fun `rota de motorista recusa gestor com 403`() {
        mockMvc.perform(post("/turnos/abrir").token("tg").json("""{"login":"gestor1"}"""))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/turnos/abrir").token("tm").json("""{"login":"ana"}"""))
            .andExpect(status().isOk)
    }

    @Test
    fun `rotas novas da 6B sao so do motorista`() {
        mockMvc.perform(post("/diarios/${java.util.UUID.randomUUID()}/fechar").token("tg").json("""{"medidorFinal":10}"""))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/diarios/${java.util.UUID.randomUUID()}/fechar").token("tm").json("""{"medidorFinal":10}"""))
            .andExpect(status().isOk)
        mockMvc.perform(post("/rastreamento/lote").token("tg").json("""{"diarioId":"x","pontos":[]}"""))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/rastreamento/lote").token("tm").json("""{"diarioId":"x","pontos":[]}"""))
            .andExpect(status().isOk)
    }

    @Test
    fun `rotas de ambos aceitam os dois perfis`() {
        mockMvc.perform(get("/veiculos/disponiveis").token("tm")).andExpect(status().isOk)
        mockMvc.perform(get("/veiculos/disponiveis").token("tg")).andExpect(status().isOk)
        mockMvc.perform(get("/postos").token("tm")).andExpect(status().isOk)
        mockMvc.perform(get("/checklist/ativo").token("tm")).andExpect(status().isNotFound) // passa da segurança
    }

    @Test
    fun `motorista so ve os proprios dados e gestor ve todos`() {
        mockMvc.perform(get("/abastecimentos/aberto/beto").token("tm"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.erro").value("Você só pode acessar os seus próprios dados."))
        mockMvc.perform(get("/abastecimentos/aberto/ana").token("tm")).andExpect(status().isOk)
        mockMvc.perform(get("/abastecimentos/aberto/beto").token("tg")).andExpect(status().isOk)
        // rota antiga com status fixo: continua 400, com a mensagem
        mockMvc.perform(post("/diarios/fechar/beto").token("tm").json("""{"medidorFinal":10}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Você só pode acessar os seus próprios dados."))
    }

    @Test
    fun `com token de motorista vale o login do token e nao o do corpo`() {
        var recebido: AbastecimentoRequest? = null
        given(abastecimentoService.registrar(qualquer())).willAnswer { recebido = it.arguments[0] as AbastecimentoRequest; null }
        mockMvc.perform(post("/abastecimentos").token("tm")
            .json("""{"loginMotorista":"beto","tipoCombustivel":"diesel","litros":10,"valorTotal":60}"""))
            .andExpect(status().isOk)
        assertEquals("ana", recebido?.loginMotorista)
    }

    @Test
    fun `cors aceita so as origens configuradas`() {
        mockMvc.perform(options("/veiculos").header("Origin", "http://localhost:4200")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization"))
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
        mockMvc.perform(options("/veiculos").header("Origin", "http://site-estranho.com")
            .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden)
    }
}
