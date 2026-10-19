package com.example.sistemaobras.controller

import jakarta.validation.Valid
import com.example.sistemaobras.dto.AtualizarStatusOcorrenciaRequest
import com.example.sistemaobras.dto.OcorrenciaDetalhadaResponse
import com.example.sistemaobras.dto.OcorrenciaRequest
import com.example.sistemaobras.dto.OcorrenciaResponse
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.service.OcorrenciaService
import com.example.sistemaobras.security.ControleAcesso
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ocorrencias")
class OcorrenciaController(
    private val ocorrenciaService: OcorrenciaService
) {

    @PostMapping
    @StatusErroFixo(400)
    fun registrar(@Valid @RequestBody request: OcorrenciaRequest): ResponseEntity<OcorrenciaResponse> {
        return ResponseEntity.ok(ocorrenciaService.registrar(request.copy(loginMotorista = ControleAcesso.loginEfetivo(request.loginMotorista))))
    }

    @GetMapping("/diario/{diarioId}")
    fun listarPorDiario(@PathVariable diarioId: String): ResponseEntity<List<OcorrenciaResponse>> {
        return ResponseEntity.ok(ocorrenciaService.listarPorDiario(diarioId))
    }

    @GetMapping("/aberto/{login}")
    fun listarDiarioAberto(@PathVariable login: String): ResponseEntity<List<OcorrenciaResponse>> {
        ControleAcesso.conferirProprioLogin(login)
        return ResponseEntity.ok(ocorrenciaService.listarDiarioAberto(login))
    }

    @GetMapping("/veiculo/{veiculoId}")
    fun listarPorVeiculo(@PathVariable veiculoId: String): ResponseEntity<List<OcorrenciaDetalhadaResponse>> {
        return ResponseEntity.ok(ocorrenciaService.listarPorVeiculo(veiculoId))
    }

    @GetMapping("/motorista/{login}")
    fun listarPorMotorista(@PathVariable login: String): ResponseEntity<List<OcorrenciaDetalhadaResponse>> {
        ControleAcesso.conferirProprioLogin(login)
        return ResponseEntity.ok(ocorrenciaService.listarPorMotorista(login))
    }

    // ?status=pendente|em_andamento|resolvida (opcional) filtra a lista; outro valor -> 400 STATUS_INVALIDO.
    @GetMapping
    fun listarTodas(@RequestParam(required = false) status: String?): ResponseEntity<List<OcorrenciaDetalhadaResponse>> {
        return ResponseEntity.ok(ocorrenciaService.listarTodas(status))
    }

    // Rota nova: erros com status coerentes (400 valor inválido, 404 não encontrada).
    @PutMapping("/{id}/status")
    fun atualizarStatus(
        @PathVariable id: String,
        @Valid @RequestBody request: AtualizarStatusOcorrenciaRequest
    ): ResponseEntity<OcorrenciaResponse> {
        return ResponseEntity.ok(ocorrenciaService.atualizarStatus(id, request.status))
    }
}