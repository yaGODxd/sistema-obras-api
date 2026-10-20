package com.example.sistemaobras.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import com.example.sistemaobras.dto.VeiculoEmUsoResponse
import com.example.sistemaobras.dto.VeiculoRequest
import com.example.sistemaobras.dto.VeiculoResponse
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.service.VeiculoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Veículos", description = "Frota: listas, cadastro, edição, inativar e reativar.")
@RestController
@RequestMapping("/veiculos")
class VeiculoController(
    private val veiculoService: VeiculoService
) {

    @GetMapping
    fun listarTodos(): ResponseEntity<List<VeiculoResponse>> {
        return ResponseEntity.ok(veiculoService.listarTodos())
    }

    @GetMapping("/disponiveis")
    fun listarDisponiveis(): ResponseEntity<List<VeiculoResponse>> {
        return ResponseEntity.ok(veiculoService.listarDisponiveis())
    }

    @GetMapping("/em-uso")
    fun listarEmUso(): ResponseEntity<List<VeiculoEmUsoResponse>> {
        return ResponseEntity.ok(veiculoService.listarEmUso())
    }

    @GetMapping("/{id}")
    @StatusErroFixo(404)
    fun buscarPorId(@PathVariable id: String): ResponseEntity<VeiculoResponse> {
        return ResponseEntity.ok(veiculoService.buscarPorId(id))
    }

    @PostMapping
    @StatusErroFixo(400)
    fun cadastrar(@Valid @RequestBody request: VeiculoRequest): ResponseEntity<Void> {
        veiculoService.cadastrar(request)
        return ResponseEntity.status(201).build()
    }

    @PutMapping("/{id}")
    @StatusErroFixo(400)
    fun atualizar(
        @PathVariable id: String,
        @Valid @RequestBody request: VeiculoRequest
    ): ResponseEntity<Void> {
        veiculoService.atualizar(id, request)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{id}")
    @StatusErroFixo(400)
    fun inativar(@PathVariable id: String): ResponseEntity<Void> {
        veiculoService.inativar(id)
        return ResponseEntity.ok().build()
    }
    // Rota nova: reativa ({"ativo": true}) ou inativa ({"ativo": false}).
    // 400 id inválido ou placa já usada por veículo ativo, 404 veículo não existe.
    @PutMapping("/{id}/ativo")
    fun alterarAtivo(@PathVariable id: String, @RequestBody body: Map<String, Boolean>): ResponseEntity<Void> {
        veiculoService.alterarAtivo(id, body["ativo"] ?: true)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/comboios")
    fun listarComboios(): ResponseEntity<List<VeiculoResponse>> {
        return ResponseEntity.ok(veiculoService.listarComboios())
    }

    @GetMapping("/{id}/motoristas")
    fun buscarMotoristas(@PathVariable id: String): ResponseEntity<Map<String, Any?>> {
        return ResponseEntity.ok(veiculoService.buscarMotoristas(id))
    }
}