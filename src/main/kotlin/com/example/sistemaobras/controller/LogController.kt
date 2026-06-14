package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.LogResponse
import com.example.sistemaobras.service.LogService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/logs")
class LogController(
    private val logService: LogService
) {

    @GetMapping
    fun listar(
        @RequestParam(required = false) login: String?,
        @RequestParam(required = false) acao: String?,
        @RequestParam(required = false) inicio: String?,
        @RequestParam(required = false) fim: String?
    ): ResponseEntity<List<LogResponse>> {
        return if (login == null && acao == null && inicio == null && fim == null) {
            ResponseEntity.ok(logService.listarTodos())
        } else {
            ResponseEntity.ok(logService.filtrar(login, acao, inicio, fim))
        }
    }
}