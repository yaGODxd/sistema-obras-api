package com.example.sistemaobras.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import com.example.sistemaobras.dto.AbastecimentoDetalhadoResponse
import com.example.sistemaobras.dto.AbastecimentoRequest
import com.example.sistemaobras.dto.AbastecimentoResponse
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.service.AbastecimentoService
import com.example.sistemaobras.service.DiarioDoEvento
import com.example.sistemaobras.security.ControleAcesso
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Abastecimentos", description = "Registro (inclusive comboio e offline) e consultas.")
@RestController
@RequestMapping("/abastecimentos")
class AbastecimentoController(
    private val abastecimentoService: AbastecimentoService,
    private val diarioDoEvento: DiarioDoEvento
) {

    @Operation(
        summary = "Registrar abastecimento (motorista)",
        description = "Diário: diarioId; no comboio (veiculoAbastecidoId) o diário do veículo abastecido no horário do evento; " +
            "senão o diário aberto agora. Diário assinado: DIARIO_ASSINADO. Erros: 400 (com codigo)."
    )
    @PostMapping
    @StatusErroFixo(400)
    fun registrar(@Valid @RequestBody request: AbastecimentoRequest): ResponseEntity<AbastecimentoResponse> {
        return ResponseEntity.ok(abastecimentoService.registrar(request.copy(loginMotorista = ControleAcesso.loginEfetivo(request.loginMotorista))))
    }

    @GetMapping("/diario/{diarioId}")
    fun listarPorDiario(@PathVariable diarioId: String): ResponseEntity<List<AbastecimentoResponse>> {
        // Parte 6B: motorista só vê o próprio diário.
        diarioDoEvento.porIdSeExistir(diarioId)?.let { diarioDoEvento.conferirDono(it) }
        return ResponseEntity.ok(abastecimentoService.listarPorDiario(diarioId))
    }

    @GetMapping("/aberto/{login}")
    fun listarDiarioAberto(@PathVariable login: String): ResponseEntity<List<AbastecimentoResponse>> {
        ControleAcesso.conferirProprioLogin(login)
        return ResponseEntity.ok(abastecimentoService.listarDiarioAberto(login))
    }

    @GetMapping("/veiculo/{veiculoId}")
    fun listarPorVeiculo(@PathVariable veiculoId: String): ResponseEntity<List<AbastecimentoDetalhadoResponse>> {
        return ResponseEntity.ok(abastecimentoService.listarPorVeiculo(veiculoId))
    }

    @GetMapping("/motorista/{login}")
    fun listarPorMotorista(@PathVariable login: String): ResponseEntity<List<AbastecimentoDetalhadoResponse>> {
        ControleAcesso.conferirProprioLogin(login)
        return ResponseEntity.ok(abastecimentoService.listarPorMotorista(login))
    }

    @GetMapping
    fun listarTodos(): ResponseEntity<List<AbastecimentoDetalhadoResponse>> {
        return ResponseEntity.ok(abastecimentoService.listarTodos())
    }
}