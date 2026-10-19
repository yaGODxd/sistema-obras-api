package com.example.sistemaobras.security

import com.example.sistemaobras.exception.ProibidoException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Quem está chamando, pelo token. Regras:
 *  - motorista só age sobre o próprio login;
 *  - gestor pode agir sobre qualquer um;
 *  - sem token (permitido enquanto app.seguranca.exigir-jwt=false) vale o comportamento antigo.
 */
object ControleAcesso {

    private fun autenticacao(): Authentication? =
        SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated && it !is AnonymousAuthenticationToken }

    fun loginDoToken(): String? = autenticacao()?.name

    fun ehMotorista(): Boolean =
        autenticacao()?.authorities?.any { it.authority == "ROLE_MOTORISTA" } == true

    // Rotas com {login} na URL: motorista com token de outro login é recusado.
    fun conferirProprioLogin(login: String) {
        if (ehMotorista() && loginDoToken() != login)
            throw ProibidoException("Você só pode acessar os seus próprios dados.")
    }

    // Corpo com loginMotorista: com token de motorista vale o login do token.
    fun loginEfetivo(loginDoCorpo: String): String =
        if (ehMotorista()) loginDoToken() ?: loginDoCorpo else loginDoCorpo
}
