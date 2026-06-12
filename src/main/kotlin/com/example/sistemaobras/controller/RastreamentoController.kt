package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.RastreamentoRequest
import com.example.sistemaobras.dto.RastreamentoResponse
import com.example.sistemaobras.dto.UltimaPosicaoResponse
import com.example.sistemaobras.service.RastreamentoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/rastreamento")
class RastreamentoController(
    private val rastreamentoService: RastreamentoService
) {

    @PostMapping
    fun registrarPonto(@RequestBody request: RastreamentoRequest): ResponseEntity<Void> {
        return try {
            rastreamentoService.registrarPonto(request)
            ResponseEntity.ok().build()
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @GetMapping("/diario/{diarioId}")
    fun buscarPontosPorDiario(@PathVariable diarioId: String): ResponseEntity<List<RastreamentoResponse>> {
        return ResponseEntity.ok(rastreamentoService.buscarPontosPorDiario(diarioId))
    }

    @GetMapping("/posicoes")
    fun buscarUltimasPosicoes(): ResponseEntity<List<UltimaPosicaoResponse>> {
        return ResponseEntity.ok(rastreamentoService.buscarUltimasPosicoes())
    }
}