package com.example.sistemaobras.controller

import jakarta.validation.Valid
import com.example.sistemaobras.dto.PostoRequest
import com.example.sistemaobras.dto.PostoResponse
import com.example.sistemaobras.entity.Posto
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.StatusErroFixo
import com.example.sistemaobras.repository.PostoRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/postos")
class PostoController(private val postoRepository: PostoRepository) {

    @GetMapping
    fun listar(): ResponseEntity<List<PostoResponse>> {
        return ResponseEntity.ok(postoRepository.findAtivos().map { toResponse(it) })
    }

    @PostMapping
    fun criar(@Valid @RequestBody request: PostoRequest): ResponseEntity<PostoResponse> {
        val posto = postoRepository.save(Posto(
            nome = request.nome.uppercase(),
            endereco = request.endereco,
            cidade = request.cidade?.uppercase()
        ))
        return ResponseEntity.ok(toResponse(posto))
    }

    @PutMapping("/{id}")
    @StatusErroFixo(400)
    fun atualizar(@PathVariable id: String, @Valid @RequestBody request: PostoRequest): ResponseEntity<PostoResponse> {
        postoRepository.findById(java.util.UUID.fromString(id))
            .orElseThrow { NaoEncontradoException("Posto não encontrado.") }
        postoRepository.atualizar(id, request.nome.uppercase(), request.endereco, request.cidade?.uppercase())
        val atualizado = postoRepository.findById(java.util.UUID.fromString(id)).orElseThrow()
        return ResponseEntity.ok(toResponse(atualizado))
    }

    @PutMapping("/{id}/ativo")
    fun alterarAtivo(@PathVariable id: String, @RequestBody body: Map<String, Boolean>): ResponseEntity<Void> {
        postoRepository.alterarAtivo(id, body["ativo"] ?: true)
        return ResponseEntity.ok().build()
    }

    private fun toResponse(p: Posto) = PostoResponse(
        id = p.id, nome = p.nome, endereco = p.endereco,
        cidade = p.cidade, ativo = p.ativo
    )
}