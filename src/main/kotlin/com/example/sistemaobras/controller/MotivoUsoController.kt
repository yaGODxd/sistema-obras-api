package com.example.sistemaobras.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import com.example.sistemaobras.dto.MotivoUsoResponse
import com.example.sistemaobras.repository.MotivoUsoRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Cadastros auxiliares", description = "Mecânicos, postos, secretarias e motivos de uso.")
@RestController
@RequestMapping("/motivos-uso")
class MotivoUsoController(
    private val motivoUsoRepository: MotivoUsoRepository
) {
    @Operation(
        summary = "Motivos de uso ativos",
        description = "Lista para o motorista escolher ao abrir o diário. O id vai em motivoUsoId do POST /diarios/abrir."
    )
    @GetMapping
    fun listar(): ResponseEntity<List<MotivoUsoResponse>> {
        return ResponseEntity.ok(
            motivoUsoRepository.findByAtivoTrueOrderByDescricao().map { MotivoUsoResponse(it.id, it.descricao) }
        )
    }
}
