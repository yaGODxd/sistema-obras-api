package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.AbastecimentoDetalhadoResponse
import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.AbastecimentoResponse
import com.example.sistemaobras.service.AbastecimentoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/abastecimentos")
class AbastecimentoController(
    private val abastecimentoService: AbastecimentoService
) {

    @PostMapping
    fun registrar(@RequestBody request: AbastecimentoRequest): ResponseEntity<AbastecimentoResponse> {
        return try {
            ResponseEntity.ok(abastecimentoService.registrar(request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @GetMapping("/diario/{diarioId}")
    fun listarPorDiario(@PathVariable diarioId: String): ResponseEntity<List<AbastecimentoResponse>> {
        return ResponseEntity.ok(abastecimentoService.listarPorDiario(diarioId))
    }

    @GetMapping("/aberto/{login}")
    fun listarDiarioAberto(@PathVariable login: String): ResponseEntity<List<AbastecimentoResponse>> {
        return ResponseEntity.ok(abastecimentoService.listarDiarioAberto(login))
    }

    @GetMapping("/veiculo/{veiculoId}")
    fun listarPorVeiculo(@PathVariable veiculoId: String): ResponseEntity<List<AbastecimentoDetalhadoResponse>> {
        return ResponseEntity.ok(abastecimentoService.listarPorVeiculo(veiculoId))
    }

    @GetMapping("/motorista/{login}")
    fun listarPorMotorista(@PathVariable login: String): ResponseEntity<List<AbastecimentoDetalhadoResponse>> {
        return ResponseEntity.ok(abastecimentoService.listarPorMotorista(login))
    }

    @GetMapping
    fun listarTodos(): ResponseEntity<List<AbastecimentoDetalhadoResponse>> {
        return ResponseEntity.ok(abastecimentoService.listarTodos())
    }
}