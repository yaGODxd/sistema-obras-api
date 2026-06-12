package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.VeiculoEmUsoResponse
import com.example.sistemaobras.dto.VeiculoRequest
import com.example.sistemaobras.dto.VeiculoResponse
import com.example.sistemaobras.service.VeiculoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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
    fun buscarPorId(@PathVariable id: String): ResponseEntity<VeiculoResponse> {
        return try {
            ResponseEntity.ok(veiculoService.buscarPorId(id))
        } catch (e: RuntimeException) {
            ResponseEntity.status(404).build()
        }
    }

    @PostMapping
    fun cadastrar(@RequestBody request: VeiculoRequest): ResponseEntity<Void> {
        return try {
            veiculoService.cadastrar(request)
            ResponseEntity.status(201).build()
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @PutMapping("/{id}")
    fun atualizar(
        @PathVariable id: String,
        @RequestBody request: VeiculoRequest
    ): ResponseEntity<Void> {
        return try {
            veiculoService.atualizar(id, request)
            ResponseEntity.ok().build()
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @DeleteMapping("/{id}")
    fun inativar(@PathVariable id: String): ResponseEntity<Void> {
        return try {
            veiculoService.inativar(id)
            ResponseEntity.ok().build()
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }
}