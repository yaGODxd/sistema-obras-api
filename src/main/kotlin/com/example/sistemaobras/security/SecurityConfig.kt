package com.example.sistemaobras.security

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

private typealias Regras = AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry

@Configuration
@EnableWebSecurity
class SecurityConfig {

    // APP_CORS_ORIGENS: origens do front separadas por vírgula
    // (ex.: http://localhost:4200,https://meu-front.com). Sem a variável, aceita qualquer origem (como antes).
    @Bean
    fun corsConfigurationSource(@Value("\${app.cors.origens:*}") origens: String): CorsConfigurationSource {
        val config = CorsConfiguration()
        val lista = origens.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (lista.isEmpty() || lista.contains("*")) config.allowedOriginPatterns = listOf("*")
        else config.allowedOrigins = lista
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        // "*" inclui o Authorization (o front manda o token nele).
        config.allowedHeaders = listOf("*")
        config.allowCredentials = false

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    /**
     * APP_SEGURANCA_EXIGIR_JWT=true liga a matriz de permissões (rota x perfil).
     * Desligado (padrão): as rotas antigas continuam abertas, como antes da Parte 5.
     * Trocar a variável no Render só reinicia a aplicação; não precisa de novo jar.
     */
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtService: JwtService,
        // Qualifier: o Spring MVC também registra um CorsConfigurationSource.
        @Qualifier("corsConfigurationSource") cors: CorsConfigurationSource,
        @Value("\${app.seguranca.exigir-jwt:false}") exigirJwt: Boolean
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(cors) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Sem token (ou token inválido) em rota protegida: 401. Sem permissão: 403. Corpo {"erro"}.
            .exceptionHandling {
                it.authenticationEntryPoint(EntradaNaoAutenticada())
                it.accessDeniedHandler(AcessoNegado())
            }
            .addFilterBefore(JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                // Página interna de erros do Spring. Liberada para que 404 e 500
                // apareçam como são, em vez de virarem 401.
                auth.requestMatchers("/error").permitAll()
                // Parte 7: documentação. Só existe com SWAGGER_ENABLED=true (senão 404).
                auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                if (exigirJwt) matrizDePermissoes(auth) else regrasAntigas(auth)
            }
        return http.build()
    }

    // Comportamento até a Parte 4: só as rotas de assinatura exigem token.
    private fun regrasAntigas(auth: Regras) {
        auth.requestMatchers(HttpMethod.GET, "/auth/me").authenticated()
        auth.requestMatchers("/usuarios/me/**").authenticated()
        auth.requestMatchers(HttpMethod.GET, "/diarios/meus").authenticated()
        auth.requestMatchers(HttpMethod.PUT, "/diarios/*").authenticated()
        auth.requestMatchers(HttpMethod.POST, "/diarios/*/assinar").authenticated()
        auth.requestMatchers(HttpMethod.GET, "/diarios/*/verificar").authenticated()
        auth.requestMatchers(
            "/auth/**", "/usuarios/**", "/turnos/**", "/veiculos/**", "/diarios/**",
            "/rastreamento/**", "/abastecimentos/**", "/ocorrencias/**", "/manutencoes/**",
            "/logs/**", "/secretarias/**", "/mecanicos/**", "/postos/**", "/checklist/**", "/motivos-uso/**",
            "/fotos/**"
        ).permitAll()
        auth.anyRequest().authenticated()
    }

    // Matriz aprovada na Parte 5. A ordem importa: a primeira regra que casa vale.
    // "Ambos" = gestor ou motorista; o motorista ainda só vê os próprios dados (ControleAcesso).
    private fun matrizDePermissoes(auth: Regras) {
        val gestor = "GESTOR"
        val motorista = "MOTORISTA"

        // Público
        auth.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()

        // ---- Só motorista ----
        auth.requestMatchers(HttpMethod.POST, "/turnos/abrir", "/turnos/fechar").hasRole(motorista)
        auth.requestMatchers(HttpMethod.POST, "/diarios/abrir", "/diarios/fechar/*", "/diarios/*/fechar", "/diarios/*/assinar").hasRole(motorista)
        auth.requestMatchers(HttpMethod.GET, "/diarios/meus").hasRole(motorista)
        auth.requestMatchers(HttpMethod.PUT, "/diarios/*").hasRole(motorista)
        auth.requestMatchers(HttpMethod.POST, "/abastecimentos", "/ocorrencias", "/rastreamento", "/rastreamento/lote", "/checklist/respostas").hasRole(motorista)
        auth.requestMatchers(HttpMethod.POST, "/fotos").hasRole(motorista)

        // ---- Só gestor (antes das regras "ambos" que usam curinga) ----
        auth.requestMatchers(HttpMethod.GET, "/usuarios", "/usuarios/motoristas/online").hasRole(gestor)
        auth.requestMatchers(HttpMethod.GET, "/diarios", "/diarios/resumo-mensal").hasRole(gestor)
        auth.requestMatchers(HttpMethod.GET, "/veiculos/em-uso", "/veiculos/*/motoristas").hasRole(gestor)
        auth.requestMatchers(HttpMethod.GET, "/abastecimentos", "/abastecimentos/veiculo/*").hasRole(gestor)
        auth.requestMatchers(HttpMethod.GET, "/ocorrencias", "/ocorrencias/veiculo/*").hasRole(gestor)
        auth.requestMatchers(HttpMethod.GET, "/rastreamento/posicoes").hasRole(gestor)
        // Antes de GET /fotos/* (ambos): senão "uso" casaria como id de foto.
        auth.requestMatchers(HttpMethod.GET, "/fotos/uso").hasRole(gestor)

        // ---- Ambos ----
        auth.requestMatchers(HttpMethod.GET, "/auth/me").hasAnyRole(gestor, motorista)
        auth.requestMatchers("/usuarios/me/**").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/usuarios/*", "/usuarios/*/resumo-semanal").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.PUT, "/usuarios/*/perfil").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/turnos/status/*").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/veiculos", "/veiculos/**").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/diarios/aberto/*", "/diarios/*", "/diarios/*/verificar").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/fotos/*", "/diarios/*/fotos").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/abastecimentos/**", "/ocorrencias/**").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/rastreamento/diario/*").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/postos", "/secretarias", "/secretarias/**", "/motivos-uso").hasAnyRole(gestor, motorista)
        auth.requestMatchers(HttpMethod.GET, "/checklist/**").hasAnyRole(gestor, motorista)

        // ---- Todo o resto: gestor (cadastros, manutenções, logs, mecânicos, gravações de gestão) ----
        auth.anyRequest().hasRole(gestor)
    }
}
