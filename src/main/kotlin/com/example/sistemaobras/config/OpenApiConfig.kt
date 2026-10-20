package com.example.sistemaobras.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Parte 7: documentação da API (Swagger/OpenAPI), para orientador, banca e outros devs.
 * Página: /swagger-ui.html  |  JSON: /v3/api-docs
 * Liga e desliga com SWAGGER_ENABLED (padrão desligado).
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("SGFE - API do Sistema de Gestão de Frota")
                .version("1.0")
                .description(DESCRICAO)
        )
        .components(
            Components()
                .addSecuritySchemes(
                    ESQUEMA_TOKEN,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token devolvido por POST /auth/login. Clique em Authorize e cole só o token.")
                )
        )
        .addSecurityItem(SecurityRequirement().addList(ESQUEMA_TOKEN))

    // Toda rota ganha a resposta "default" com o formato {"erro", "codigo"}.
    // O esquema é criado aqui (e não no bean acima) porque o springdoc remove esquemas sem referência.
    @Bean
    fun respostaDeErroPadrao(): OpenApiCustomizer = OpenApiCustomizer { api ->
        api.components.addSchemas(
            "Erro",
            ObjectSchema()
                .description("Corpo de toda resposta de erro.")
                .addProperty("erro", StringSchema().description("Mensagem em português.").example("Motorista não tem diário aberto"))
                .addProperty("codigo", StringSchema().description("Código estável (só em alguns erros).").example("SEM_DIARIO_ABERTO"))
                .addRequiredItem("erro")
        )
        val erro = ApiResponse()
            .description("Erro. Corpo {\"erro\": \"...\", \"codigo\": \"...\"}. Ver a tabela de status na descrição da API.")
            .content(Content().addMediaType("application/json", MediaType().schema(io.swagger.v3.oas.models.media.Schema<Any>().`$ref`("#/components/schemas/Erro"))))
        api.paths?.values?.forEach { caminho ->
            caminho.readOperations().forEach { operacao ->
                if (operacao.responses?.containsKey("default") != true) operacao.responses?.addApiResponse("default", erro)
            }
        }
    }

    companion object {
        const val ESQUEMA_TOKEN = "token"

        private val DESCRICAO = """
            API do SGFE: gestor (front Angular) e motorista (app Android).

            **Autenticação**: POST /auth/login devolve o token. Envie em `Authorization: Bearer <token>`.
            Com APP_SEGURANCA_EXIGIR_JWT=true vale a matriz de permissões (gestor, motorista, ambos).

            **Datas**: o banco guarda UTC. Por padrão as datas saem sem fuso (são UTC);
            com APP_DATAS_COM_FUSO=true saem com Z. Nos envios, `ocorridoEm`/`enviadoEm` usam ISO-8601 com fuso.

            **Uso offline (Parte 6B)**: `ocorridoEm` (hora do evento no celular), `idLocal` (UUID; reenvio não duplica),
            `enviadoEm` e `diarioId` são opcionais. Horário suspeito é aceito e marcado (`horarioSuspeito`, `motivoSuspeita`).

            **Erros**: sempre `{"erro": "mensagem"}`, às vezes com `"codigo"`.
            Rotas antigas mantêm o status de erro de antes (em geral 400) por compatibilidade; o `codigo` diz o motivo.

            | Status | Quando | Tentar de novo? |
            |---|---|---|
            | 400 | dado inválido ou regra violada | não |
            | 401 | sem token ou token vencido | não (fazer login) |
            | 403 | sem permissão / diário de outro motorista | não |
            | 404 | não encontrado | não |
            | 409 | conflito de estado (diário já fechado, assinado, idLocal em uso) | não; REENVIO_SIMULTANEO: sim |
            | 422 | horário mais de 24 h no futuro | não |
            | 5xx | erro do servidor | sim |

            **Códigos**: PIN_AUSENTE, DIARIO_PENDENTE_ASSINATURA, TURNO_COM_DIARIO_ABERTO, STATUS_INVALIDO, ID_INVALIDO,
            HORARIO_INVALIDO, HORARIO_FUTURO, DIARIO_NAO_ENCONTRADO, SEM_DIARIO_ABERTO, DIARIO_DE_OUTRO_MOTORISTA,
            DIARIO_ASSINADO, DIARIO_JA_FECHADO, ID_LOCAL_EM_USO, REENVIO_SIMULTANEO, LOTE_GRANDE_DEMAIS,
            PLACA_EM_USO, PLACA_DE_VEICULO_INATIVO.
        """.trimIndent()
    }
}
