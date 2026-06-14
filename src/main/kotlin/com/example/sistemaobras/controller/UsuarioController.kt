package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.AtualizarPerfilRequest
import com.example.sistemaobras.dto.MotoristaOnlineResponse
import com.example.sistemaobras.dto.UsuarioResponse
import com.example.sistemaobras.service.UsuarioService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/usuarios")
class UsuarioController(
    private val usuarioService: UsuarioService
) {
    @GetMapping("/{login}")
    fun buscarPerfil(@PathVariable login: String): ResponseEntity<UsuarioResponse> {
        return try {
            ResponseEntity.ok(usuarioService.buscarPerfil(login))
        } catch (e: RuntimeException) {
            ResponseEntity.status(404).build()
        }
    }

    @GetMapping("/motoristas/online")
    fun listarMotoristasOnline(): ResponseEntity<List<MotoristaOnlineResponse>> {
        return ResponseEntity.ok(usuarioService.listarMotoristasOnline())
    }

    @PutMapping("/{login}/perfil")
    fun atualizarPerfil(
        @PathVariable login: String,
        @RequestBody request: AtualizarPerfilRequest
    ): ResponseEntity<UsuarioResponse> {
        return try {
            ResponseEntity.ok(usuarioService.atualizarPerfil(login, request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @GetMapping
    fun listarTodos(): ResponseEntity<List<UsuarioResponse>> {
        return ResponseEntity.ok(usuarioService.listarTodos())
    }

    @PutMapping("/{login}/ativo")
    fun alterarAtivo(
        @PathVariable login: String,
        @RequestBody body: Map<String, Boolean>
    ): ResponseEntity<Void> {
        return try {
            usuarioService.alterarAtivo(login, body["ativo"] ?: true)
            ResponseEntity.ok().build()
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }
}