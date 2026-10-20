package com.example.sistemaobras.controller

import jakarta.validation.Valid
import com.example.sistemaobras.dto.AbrirDiarioRequest
import com.example.sistemaobras.dto.DiarioDetalheResponse
import com.example.sistemaobras.dto.DiarioResponse
import com.example.sistemaobras.dto.DiarioResumoMensalResponse
import com.example.sistemaobras.dto.FecharDiarioRequest
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.service.DiarioBordoService
import com.example.sistemaobras.security.ControleAcesso
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
        return ResponseEntity.ok(diarioService.abrirDiario(request.copy(loginMotorista = ControleAcesso.loginEfetivo(request.loginMotorista))))
    }

    @PostMapping("/fechar/{login}")
    @StatusErroFixo(400)
    fun fecharDiario(
        @PathVariable login: String,
        @Valid @RequestBody request: FecharDiarioRequest
    ): ResponseEntity<DiarioResponse> {
        ControleAcesso.conferirProprioLogin(login)
        return ResponseEntity.ok(diarioService.fecharDiario(login, request))
    }

    // Rota nova (Parte 6B): fecha pelo id do diário, com ocorridoEm e idLocal (idempotente).
    // 400 dado inválido, 403 diário de outro motorista, 404 não existe, 409 já fechado/idLocal em uso, 422 horário.
    @PostMapping("/{id}/fechar")
    fun fecharDiarioPorId(
        @PathVariable id: String,
        @Valid @RequestBody request: FecharDiarioRequest
    ): ResponseEntity<DiarioResponse> {
        return ResponseEntity.ok(diarioService.fecharDiarioPorId(id, request))
    }

    @GetMapping("/aberto/{login}")
    fun buscarDiarioAberto(@PathVariable login: String): ResponseEntity<DiarioResponse> {
        ControleAcesso.conferirProprioLogin(login)
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

    // Detalhe de um diário (modal do front). Rota nova: 400 id inválido, 404 não encontrado.
    @GetMapping("/{id}")
    fun buscarDetalhe(@PathVariable id: String): ResponseEntity<DiarioDetalheResponse> {
        val diario = diarioService.buscarDetalhe(id)
        // Motorista só vê o próprio diário.
        diario.motoristaLogin?.let { ControleAcesso.conferirProprioLogin(it) }
        return ResponseEntity.ok(diario)
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