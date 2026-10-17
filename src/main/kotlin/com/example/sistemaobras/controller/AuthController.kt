package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.CriarUsuarioRequest
import com.example.sistemaobras.dto.LoginRequest
import com.example.sistemaobras.dto.LoginResponse
import com.example.sistemaobras.service.UsuarioService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val usuarioService: UsuarioService
) {
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return try {
            val response = usuarioService.login(request)
            ResponseEntity.ok(response)
        } catch (e: RuntimeException) {
            ResponseEntity.status(401).build()
        }
    }

    @PostMapping("/cadastrar")
    fun cadastrar(@RequestBody request: CriarUsuarioRequest): ResponseEntity<LoginResponse> {
        return try {
            val response = usuarioService.criar(request)
            ResponseEntity.status(201).body(response)
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    // Devolve quem é o dono do token. Serve para testar a autenticação.
    @GetMapping("/me")
    fun me(authentication: Authentication): ResponseEntity<Map<String, Any?>> {
        val perfil = authentication.authorities.firstOrNull()
            ?.authority?.removePrefix("ROLE_")?.lowercase()
        return ResponseEntity.ok(mapOf("login" to authentication.name, "perfil" to perfil))
    }
}