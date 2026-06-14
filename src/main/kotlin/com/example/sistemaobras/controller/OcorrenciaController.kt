package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.OcorrenciaDetalhadaResponse
import com.example.sistemaobras.dto.OcorrenciaRequest
import com.example.sistemaobras.dto.OcorrenciaResponse
import com.example.sistemaobras.service.OcorrenciaService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ocorrencias")
class OcorrenciaController(
    private val ocorrenciaService: OcorrenciaService
) {

    @PostMapping
    fun registrar(@RequestBody request: OcorrenciaRequest): ResponseEntity<OcorrenciaResponse> {
        return try {
            ResponseEntity.ok(ocorrenciaService.registrar(request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @GetMapping("/diario/{diarioId}")
    fun listarPorDiario(@PathVariable diarioId: String): ResponseEntity<List<OcorrenciaResponse>> {
        return ResponseEntity.ok(ocorrenciaService.listarPorDiario(diarioId))
    }

    @GetMapping("/aberto/{login}")
    fun listarDiarioAberto(@PathVariable login: String): ResponseEntity<List<OcorrenciaResponse>> {
        return ResponseEntity.ok(ocorrenciaService.listarDiarioAberto(login))
    }

    @GetMapping("/veiculo/{veiculoId}")
    fun listarPorVeiculo(@PathVariable veiculoId: String): ResponseEntity<List<OcorrenciaDetalhadaResponse>> {
        return ResponseEntity.ok(ocorrenciaService.listarPorVeiculo(veiculoId))
    }

    @GetMapping("/motorista/{login}")
    fun listarPorMotorista(@PathVariable login: String): ResponseEntity<List<OcorrenciaDetalhadaResponse>> {
        return ResponseEntity.ok(ocorrenciaService.listarPorMotorista(login))
    }

    @GetMapping
    fun listarTodas(): ResponseEntity<List<OcorrenciaDetalhadaResponse>> {
        return ResponseEntity.ok(ocorrenciaService.listarTodas())
    }
}