package com.example.sistemaobras.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler

// Respostas 401 e 403 do Spring Security no mesmo formato {"erro"} da API.
// Os status continuam os mesmos de antes.

class EntradaNaoAutenticada : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        escreverErro(response, HttpServletResponse.SC_UNAUTHORIZED, "Não autenticado. Faça login novamente.")
    }
}

class AcessoNegado : AccessDeniedHandler {
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        escreverErro(response, HttpServletResponse.SC_FORBIDDEN, "Você não tem permissão para acessar este recurso.")
    }
}

// As mensagens são fixas e não têm aspas, então o JSON pode ser montado à mão.
private fun escreverErro(response: HttpServletResponse, status: Int, mensagem: String) {
    response.status = status
    response.contentType = "application/json"
    response.characterEncoding = "UTF-8"
    response.writer.write("{\"erro\":\"$mensagem\"}")
}
