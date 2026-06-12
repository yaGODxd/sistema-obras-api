package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.TurnoRequest
import com.example.sistemaobras.dto.TurnoResponse
import com.example.sistemaobras.service.TurnoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/turnos")
class TurnoController(
    private val turnoService: TurnoService
) {

    @PostMapping("/abrir")
    fun abrirTurno(@RequestBody request: TurnoRequest): ResponseEntity<TurnoResponse> {
        return try {
            ResponseEntity.ok(turnoService.abrirTurno(request.login))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @PostMapping("/fechar")
    fun fecharTurno(@RequestBody request: TurnoRequest): ResponseEntity<TurnoResponse> {
        return try {
            ResponseEntity.ok(turnoService.fecharTurno(request.login))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @GetMapping("/status/{login}")
    fun verificarTurno(@PathVariable login: String): ResponseEntity<Map<String, Boolean>> {
        return ResponseEntity.ok(mapOf("turnoAberto" to turnoService.temTurnoAberto(login)))
    }
}