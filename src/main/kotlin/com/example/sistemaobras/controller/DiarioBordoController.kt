package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.AbrirDiarioRequest
import com.example.sistemaobras.dto.DiarioResponse
import com.example.sistemaobras.dto.FecharDiarioRequest
import com.example.sistemaobras.service.DiarioBordoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/diarios")
class DiarioBordoController(
    private val diarioService: DiarioBordoService
) {

    @PostMapping("/abrir")
    fun abrirDiario(@RequestBody request: AbrirDiarioRequest): ResponseEntity<DiarioResponse> {
        return try {
            ResponseEntity.ok(diarioService.abrirDiario(request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @PostMapping("/fechar/{login}")
    fun fecharDiario(
        @PathVariable login: String,
        @RequestBody request: FecharDiarioRequest
    ): ResponseEntity<DiarioResponse> {
        return try {
            ResponseEntity.ok(diarioService.fecharDiario(login, request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @GetMapping("/aberto/{login}")
    fun buscarDiarioAberto(@PathVariable login: String): ResponseEntity<DiarioResponse> {
        val diario = diarioService.buscarDiarioAberto(login)
        return if (diario != null) ResponseEntity.ok(diario)
        else ResponseEntity.status(404).build()
    }
}