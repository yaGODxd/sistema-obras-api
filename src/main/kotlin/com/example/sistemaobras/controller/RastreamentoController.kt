package com.example.sistemaobras.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import com.example.sistemaobras.dto.RastreamentoLoteRequest
import com.example.sistemaobras.dto.RastreamentoLoteResponse
import com.example.sistemaobras.dto.RastreamentoRequest
import com.example.sistemaobras.service.DiarioDoEvento
import jakarta.validation.Valid
import com.example.sistemaobras.dto.RastreamentoResponse
import com.example.sistemaobras.dto.UltimaPosicaoResponse
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.service.RastreamentoService
import com.example.sistemaobras.security.ControleAcesso
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Rastreamento", description = "Pontos de GPS (um a um ou em lote de até 200) e posições atuais.")
@RestController
@RequestMapping("/rastreamento")
class RastreamentoController(
    private val rastreamentoService: RastreamentoService,
    private val diarioDoEvento: DiarioDoEvento
) {

    @PostMapping
    @StatusErroFixo(400)
    fun registrarPonto(@RequestBody request: RastreamentoRequest): ResponseEntity<Void> {
        rastreamentoService.registrarPonto(request.copy(loginMotorista = ControleAcesso.loginEfetivo(request.loginMotorista)))
        return ResponseEntity.ok().build()
    }

    // Rota nova (Parte 6B): pontos guardados no celular (no máximo 200 por chamada).
    // Responde 200 com o resultado de cada ponto; 403 diário de outro motorista, 404 não existe, 409 assinado.
    @Operation(
        summary = "Enviar pontos guardados no celular (uso offline)",
        description = "Até 200 pontos (LOTE_GRANDE_DEMAIS). Cada ponto volta como gravado, duplicado, " +
            "descartado_proximo (a menos de 50 m e 2 min de outro) ou recusado (com codigo). Aceita diário fechado, não assinado."
    )
    @PostMapping("/lote")
    fun registrarLote(@Valid @RequestBody request: RastreamentoLoteRequest): ResponseEntity<RastreamentoLoteResponse> {
        return ResponseEntity.ok(rastreamentoService.registrarLote(request))
    }

    @GetMapping("/diario/{diarioId}")
    fun buscarPontosPorDiario(@PathVariable diarioId: String): ResponseEntity<List<RastreamentoResponse>> {
        diarioDoEvento.porIdSeExistir(diarioId)?.let { diarioDoEvento.conferirDono(it) }
        return ResponseEntity.ok(rastreamentoService.buscarPontosPorDiario(diarioId))
    }

    @GetMapping("/posicoes")
    fun buscarUltimasPosicoes(): ResponseEntity<List<UltimaPosicaoResponse>> {
        return ResponseEntity.ok(rastreamentoService.buscarUltimasPosicoes())
    }
}