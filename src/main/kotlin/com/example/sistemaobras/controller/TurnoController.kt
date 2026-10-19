package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.TurnoRequest
import com.example.sistemaobras.dto.TurnoResponse
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.service.TurnoService
import com.example.sistemaobras.security.ControleAcesso
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/turnos")
class TurnoController(
    private val turnoService: TurnoService
) {

    @PostMapping("/abrir")
    @StatusErroFixo(400)
    fun abrirTurno(@RequestBody request: TurnoRequest): ResponseEntity<TurnoResponse> {
        return ResponseEntity.ok(turnoService.abrirTurno(ControleAcesso.loginEfetivo(request.login)))
    }

    @PostMapping("/fechar")
    @StatusErroFixo(400)
    fun fecharTurno(@RequestBody request: TurnoRequest): ResponseEntity<TurnoResponse> {
        return ResponseEntity.ok(turnoService.fecharTurno(ControleAcesso.loginEfetivo(request.login)))
    }

    @GetMapping("/status/{login}")
    fun verificarTurno(@PathVariable login: String): ResponseEntity<Map<String, Boolean>> {
        ControleAcesso.conferirProprioLogin(login)
        return ResponseEntity.ok(mapOf("turnoAberto" to turnoService.temTurnoAberto(login)))
    }
}