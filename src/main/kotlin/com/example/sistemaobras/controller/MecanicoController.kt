package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.MecanicoRequest
import com.example.sistemaobras.dto.MecanicoResponse
import com.example.sistemaobras.entity.Mecanico
import com.example.sistemaobras.repository.MecanicoRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/mecanicos")
class MecanicoController(private val mecanicoRepository: MecanicoRepository) {

    @GetMapping
    fun listar(): ResponseEntity<List<MecanicoResponse>> {
        return ResponseEntity.ok(mecanicoRepository.findAtivos().map { toResponse(it) })
    }

    @PostMapping
    fun criar(@RequestBody request: MecanicoRequest): ResponseEntity<MecanicoResponse> {
        val mecanico = mecanicoRepository.save(Mecanico(
            nome = request.nome.uppercase(),
            telefone = request.telefone,
            especialidade = request.especialidade?.uppercase()
        ))
        return ResponseEntity.ok(toResponse(mecanico))
    }

    @PutMapping("/{id}")
    fun atualizar(@PathVariable id: String, @RequestBody request: MecanicoRequest): ResponseEntity<MecanicoResponse> {
        return try {
            mecanicoRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow { RuntimeException("Mecânico não encontrado") }
            mecanicoRepository.atualizar(id, request.nome.uppercase(), request.telefone, request.especialidade?.uppercase())
            val atualizado = mecanicoRepository.findById(java.util.UUID.fromString(id)).orElseThrow()
            ResponseEntity.ok(toResponse(atualizado))
        } catch (e: RuntimeException) {
            ResponseEntity.status(400).build()
        }
    }

    @PutMapping("/{id}/ativo")
    fun alterarAtivo(@PathVariable id: String, @RequestBody body: Map<String, Boolean>): ResponseEntity<Void> {
        mecanicoRepository.alterarAtivo(id, body["ativo"] ?: true)
        return ResponseEntity.ok().build()
    }

    private fun toResponse(m: Mecanico) = MecanicoResponse(
        id = m.id, nome = m.nome, telefone = m.telefone,
        especialidade = m.especialidade, ativo = m.ativo
    )
}