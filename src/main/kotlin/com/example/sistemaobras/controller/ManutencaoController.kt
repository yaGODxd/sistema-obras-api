package com.example.sistemaobras.controller

import jakarta.validation.Valid
import com.example.sistemaobras.dto.*
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.service.ManutencaoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/manutencoes")
class ManutencaoController(
    private val manutencaoService: ManutencaoService
) {

    @PostMapping
    @StatusErroFixo(400)
    fun abrir(@Valid @RequestBody request: ManutencaoRequest): ResponseEntity<ManutencaoResponse> {
        return ResponseEntity.ok(manutencaoService.abrir(request))
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
    @StatusErroFixo(404)
    fun buscarPorId(@PathVariable id: String): ResponseEntity<ManutencaoResponse> {
        return ResponseEntity.ok(manutencaoService.buscarPorId(id))
    }

    @GetMapping("/veiculo/{veiculoId}")
    fun listarPorVeiculo(@PathVariable veiculoId: String): ResponseEntity<List<ManutencaoResponse>> {
        return ResponseEntity.ok(manutencaoService.listarPorVeiculo(veiculoId))
    }

    @PutMapping("/{id}/status")
    @StatusErroFixo(400)
    fun atualizarStatus(
        @PathVariable id: String,
        @Valid @RequestBody request: AtualizarStatusRequest
    ): ResponseEntity<ManutencaoResponse> {
        return ResponseEntity.ok(manutencaoService.atualizarStatus(id, request))
    }

    @PostMapping("/{id}/pecas")
    @StatusErroFixo(400)
    fun adicionarPeca(
        @PathVariable id: String,
        @Valid @RequestBody request: PecaRequest
    ): ResponseEntity<PecaResponse> {
        return ResponseEntity.ok(manutencaoService.adicionarPeca(id, request))
    }

    // Rota nova (sem status fixo): 400, 404 ou 409 conforme o erro.
    @PutMapping("/pecas/{pecaId}")
    fun atualizarPeca(
        @PathVariable pecaId: String,
        @Valid @RequestBody request: PecaRequest
    ): ResponseEntity<PecaResponse> {
        return ResponseEntity.ok(manutencaoService.atualizarPeca(pecaId, request))
    }

    @DeleteMapping("/pecas/{pecaId}")
    @StatusErroFixo(404)
    fun removerPeca(@PathVariable pecaId: String): ResponseEntity<Void> {
        manutencaoService.removerPeca(pecaId)
        return ResponseEntity.noContent().build()
    }
}
