package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.*
import com.example.sistemaobras.service.ManutencaoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/manutencoes")
class ManutencaoController(
    private val manutencaoService: ManutencaoService
) {

    @PostMapping
    fun abrir(@RequestBody request: ManutencaoRequest): ResponseEntity<ManutencaoResponse> {
        return try {
            ResponseEntity.ok(manutencaoService.abrir(request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @GetMapping
    fun listarTodas(): ResponseEntity<List<ManutencaoResponse>> {
        return ResponseEntity.ok(manutencaoService.listarTodas())
    }

    @GetMapping("/abertas")
    fun listarAbertas(): ResponseEntity<List<ManutencaoResponse>> {
        return ResponseEntity.ok(manutencaoService.listarAbertas())
    }

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: String): ResponseEntity<ManutencaoResponse> {
        return try {
            ResponseEntity.ok(manutencaoService.buscarPorId(id))
        } catch (e: RuntimeException) {
            ResponseEntity.status(404).build()
        }
    }

    @GetMapping("/veiculo/{veiculoId}")
    fun listarPorVeiculo(@PathVariable veiculoId: String): ResponseEntity<List<ManutencaoResponse>> {
        return ResponseEntity.ok(manutencaoService.listarPorVeiculo(veiculoId))
    }

    @PutMapping("/{id}/status")
    fun atualizarStatus(
        @PathVariable id: String,
        @RequestBody request: AtualizarStatusRequest
    ): ResponseEntity<ManutencaoResponse> {
        return try {
            ResponseEntity.ok(manutencaoService.atualizarStatus(id, request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @PostMapping("/{id}/pecas")
    fun adicionarPeca(
        @PathVariable id: String,
        @RequestBody request: PecaRequest
    ): ResponseEntity<PecaResponse> {
        return try {
            ResponseEntity.ok(manutencaoService.adicionarPeca(id, request))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @DeleteMapping("/pecas/{pecaId}")
    fun removerPeca(@PathVariable pecaId: String): ResponseEntity<Void> {
        return try {
            manutencaoService.removerPeca(pecaId)
            ResponseEntity.noContent().build()
        } catch (e: RuntimeException) {
            ResponseEntity.status(404).build()
        }
    }
}