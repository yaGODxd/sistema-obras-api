package com.example.sistemaobras.controller

import jakarta.validation.Valid
import com.example.sistemaobras.dto.AbrirDiarioRequest
import com.example.sistemaobras.dto.DiarioResponse
import com.example.sistemaobras.dto.DiarioResumoMensalResponse
import com.example.sistemaobras.dto.FecharDiarioRequest
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.service.DiarioBordoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/diarios")
class DiarioBordoController(
    private val diarioService: DiarioBordoService
) {

    @PostMapping("/abrir")
    @StatusErroFixo(400)
    fun abrirDiario(@Valid @RequestBody request: AbrirDiarioRequest): ResponseEntity<DiarioResponse> {
        return ResponseEntity.ok(diarioService.abrirDiario(request))
    }

    @PostMapping("/fechar/{login}")
    @StatusErroFixo(400)
    fun fecharDiario(
        @PathVariable login: String,
        @Valid @RequestBody request: FecharDiarioRequest
    ): ResponseEntity<DiarioResponse> {
        return ResponseEntity.ok(diarioService.fecharDiario(login, request))
    }

    @GetMapping("/aberto/{login}")
    fun buscarDiarioAberto(@PathVariable login: String): ResponseEntity<DiarioResponse> {
        val diario = diarioService.buscarDiarioAberto(login)
        return if (diario != null) ResponseEntity.ok(diario)
        else ResponseEntity.status(404).build()
    }

    @GetMapping
    fun listarDiarios(
        @RequestParam(required = false) veiculoId: String?,
        @RequestParam(required = false) mes: Int?,
        @RequestParam(required = false) ano: Int?
    ): ResponseEntity<List<DiarioResponse>> {
        return ResponseEntity.ok(diarioService.listarDiarios(veiculoId, mes, ano))
    }

    @GetMapping("/resumo-mensal")
    fun listarResumoMensal(
        @RequestParam(required = false) veiculoId: String?,
        @RequestParam(required = false) mes: Int?,
        @RequestParam(required = false) ano: Int?
    ): ResponseEntity<List<DiarioResumoMensalResponse>> {
        return ResponseEntity.ok(diarioService.listarResumoMensal(veiculoId, mes, ano))
    }
}