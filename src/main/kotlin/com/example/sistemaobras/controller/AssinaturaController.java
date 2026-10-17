package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.AssinarDiarioRequest
import com.example.sistemaobras.dto.DefinirPinRequest
import com.example.sistemaobras.dto.EditarDiarioRequest
import com.example.sistemaobras.dto.PinStatusResponse
import com.example.sistemaobras.service.
import com.example.sistemaobras.service.AssinaturaService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class AssinaturaController(
        private val assinaturaService: AssinaturaService
) {

    // O app consulta se o motorista já cadastrou o PIN
    @GetMapping("/usuarios/me/pin")
    fun statusPin(auth: Authentication): ResponseEntity<Any> =
    executar { PinStatusResponse(assinaturaService.pinDefinido(auth.name)) }

    @PutMapping("/usuarios/me/pin")
    fun definirPin(auth: Authentication, @RequestBody req: DefinirPinRequest): ResponseEntity<Any> =
    executar {
        assinaturaService.definirPin(auth.name, req)
        mapOf("mensagem" to "PIN cadastrado com sucesso")
    }

    // Histórico do motorista logado, com filtros opcionais
    @GetMapping("/diarios/meus")
    fun meus(
            auth: Authentication,
            @RequestParam(required = false) mes: Int?,
            @RequestParam(required = false) ano: Int?,
            @RequestParam(required = false) status: String?
    ): ResponseEntity<Any> =
    executar { assinaturaService.historico(auth.name, mes, ano, status) }

    @PutMapping("/diarios/{id}")
    fun editar(
            auth: Authentication,
            @PathVariable id: String,
            @RequestBody req: EditarDiarioRequest
    ): ResponseEntity<Any> =
    executar { assinaturaService.editar(auth.name, id, req) }

    @PostMapping("/diarios/{id}/assinar")
    fun assinar(
            auth: Authentication,
            @PathVariable id: String,
            @RequestBody req: AssinarDiarioRequest
    ): ResponseEntity<Any> =
    executar { assinaturaService.assinar(auth.name, id, req.pin) }

    // Dono do diário ou gestor: confere se o conteúdo mudou depois da assinatura
    @GetMapping("/diarios/{id}/verificar")
    fun verificar(auth: Authentication, @PathVariable id: String): ResponseEntity<Any> {
        val ehGestor = auth.authorities.any { it.authority == "ROLE_GESTOR" }
        return executar { assinaturaService.verificar(auth.name, ehGestor, id) }
    }

    private fun executar(bloco: () -> Any): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(bloco())
        } catch (e: AssinaturaException) {
            ResponseEntity.status(e.httpStatus).body(mapOf("erro" to (e.message ?: "Erro")))
        } catch (e: RuntimeException) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf("erro" to "Não foi possível concluir a operação"))
        }
    }
}