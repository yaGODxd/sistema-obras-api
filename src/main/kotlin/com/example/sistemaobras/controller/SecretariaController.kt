package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.SecretariaResponse
import com.example.sistemaobras.repository.SecretariaRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/secretarias")
class SecretariaController(
    private val secretariaRepository: SecretariaRepository
) {
    @GetMapping
    fun listar(): ResponseEntity<List<SecretariaResponse>> {
        return ResponseEntity.ok(
            secretariaRepository.findAll().map { SecretariaResponse(it.id!!, it.nome) }
        )
    }
}