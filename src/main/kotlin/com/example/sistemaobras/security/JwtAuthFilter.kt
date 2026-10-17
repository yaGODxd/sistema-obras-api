package com.example.sistemaobras.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")

        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            if (jwtService.validarToken(token)) {
                val login = jwtService.extrairLogin(token)
                val perfil = jwtService.extrairPerfil(token)
                if (login != null && perfil != null) {
                    SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(
                            login,
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_" + perfil.uppercase()))
                        )
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}