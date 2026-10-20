package com.example.sistemaobras.config

import com.example.sistemaobras.repository.*
import com.example.sistemaobras.security.JwtService
import com.example.sistemaobras.security.SecurityConfig
import com.example.sistemaobras.service.*
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.configuration.SpringDocJacksonKotlinModuleConfiguration
import org.springdoc.core.configuration.SpringDocKotlinConfiguration
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiOAuthProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springdoc.webmvc.ui.SwaggerConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Parte 7: gera o OpenAPI de todos os controllers (sem banco), com a matriz de permissões LIGADA.
@WebMvcTest
@Import(SecurityConfig::class, OpenApiConfig::class)
@ImportAutoConfiguration(
    SpringDocConfiguration::class, SpringDocConfigProperties::class, SpringDocSpecPropertiesConfiguration::class,
    SpringDocKotlinConfiguration::class, SpringDocJacksonKotlinModuleConfiguration::class,
    SpringDocWebMvcConfiguration::class, SwaggerConfig::class, SwaggerUiConfigProperties::class, SwaggerUiOAuthProperties::class
)
@TestPropertySource(properties = [
    "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true", "app.seguranca.exigir-jwt=true"
])
class SwaggerTest {

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
    @MockitoBean lateinit var motivoUsoRepository: MotivoUsoRepository
    @MockitoBean lateinit var fotoService: FotoService
    @MockitoBean lateinit var checklistTemplateRepository: ChecklistTemplateRepository
    @MockitoBean lateinit var checklistItemRepository: ChecklistItemRepository
    @MockitoBean lateinit var checklistRespostaRepository: ChecklistRespostaRepository

    @Test
    fun `documento openapi e publico e descreve token, tags, rotas novas e o erro padrao`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title").value("SGFE - API do Sistema de Gestão de Frota"))
            .andExpect(jsonPath("$.components.securitySchemes.token.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.schemas.Erro.properties.codigo").exists())
            .andExpect(jsonPath("$.paths['/rastreamento/lote'].post").exists())
            .andExpect(jsonPath("$.paths['/diarios/{id}/fechar'].post").exists())
            .andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty)
            .andExpect(jsonPath("$.paths['/abastecimentos'].post.responses.default").exists())
            .andExpect(content().string(containsString("Diários de bordo")))
            .andExpect(content().string(containsString("Hora do evento no celular")))
    }

    @Test
    fun `pagina do swagger e publica`() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
    }
}
