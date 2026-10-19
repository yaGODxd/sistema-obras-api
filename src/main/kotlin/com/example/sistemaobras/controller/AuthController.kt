package com.example.sistemaobras.controller

import jakarta.validation.Valid
import com.example.sistemaobras.dto.CriarUsuarioRequest
import com.example.sistemaobras.dto.LoginRequest
import com.example.sistemaobras.dto.LoginResponse
import com.example.sistemaobras.exception.StatusErroFixo
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
    @StatusErroFixo(401)
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return ResponseEntity.ok(usuarioService.login(request))
    }

    @PostMapping("/cadastrar")
    @StatusErroFixo(400)
    fun cadastrar(@Valid @RequestBody request: CriarUsuarioRequest): ResponseEntity<LoginResponse> {
        return ResponseEntity.status(201).body(usuarioService.criar(request))
    }

    // Devolve quem é o dono do token. Serve para testar a autenticação.
    @GetMapping("/me")
    fun me(authentication: Authentication): ResponseEntity<Map<String, Any?>> {
        val perfil = authentication.authorities.firstOrNull()
            ?.authority?.removePrefix("ROLE_")?.lowercase()
        return ResponseEntity.ok(mapOf("login" to authentication.name, "perfil" to perfil))
    }
}