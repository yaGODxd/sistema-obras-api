package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.AtualizarPerfilRequest
import com.example.sistemaobras.dto.AtualizarUsuarioRequest
import com.example.sistemaobras.dto.MotoristaOnlineResponse
import com.example.sistemaobras.dto.ResumoSemanalResponse
import com.example.sistemaobras.dto.UsuarioResponse
import com.example.sistemaobras.repository.UsuarioRepository
import com.example.sistemaobras.service.UsuarioService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/usuarios")
class UsuarioController(
    private val usuarioService: UsuarioService,
    private val usuarioRepository: UsuarioRepository
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

    @PutMapping("/{login}")
    fun atualizarUsuario(
        @PathVariable login: String,
        @RequestBody request: AtualizarUsuarioRequest
    ): ResponseEntity<UsuarioResponse> {
        return try {
            ResponseEntity.ok(usuarioService.atualizarUsuario(login, request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @GetMapping("/{login}/resumo-semanal")
    fun resumoSemanal(@PathVariable login: String): ResponseEntity<ResumoSemanalResponse> {
        return try {
            val row = usuarioRepository.findResumoSemanal(login)
            ResponseEntity.ok(
                ResumoSemanalResponse(
                    totalDiarios = row[0].toString().toDouble().toInt(),
                    kmRodados = row[1].toString().toDouble(),
                    totalOcorrencias = row[2].toString().toDouble().toInt()
                )
            )
        } catch (e: Exception) {
            ResponseEntity.ok(ResumoSemanalResponse(0, 0.0, 0))
        }
    }
}