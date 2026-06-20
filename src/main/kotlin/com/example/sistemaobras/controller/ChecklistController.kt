package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.*
import com.example.sistemaobras.entity.ChecklistItem
import com.example.sistemaobras.entity.ChecklistResposta
import com.example.sistemaobras.entity.ChecklistTemplate
import com.example.sistemaobras.repository.ChecklistItemRepository
import com.example.sistemaobras.repository.ChecklistRespostaRepository
import com.example.sistemaobras.repository.ChecklistTemplateRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/checklist")
class ChecklistController(
    private val templateRepository: ChecklistTemplateRepository,
    private val itemRepository: ChecklistItemRepository,
    private val respostaRepository: ChecklistRespostaRepository
) {

    // ==================== TEMPLATES ====================

    @GetMapping("/templates")
    fun listarTemplates(): ResponseEntity<List<ChecklistTemplateResponse>> {
        return ResponseEntity.ok(templateRepository.findAtivos().map { t ->
            ChecklistTemplateResponse(
                id = t.id, nome = t.nome, ativo = t.ativo,
                itens = itemRepository.findByTemplateId(t.id.toString()).map { toItemResponse(it) }
            )
        })
    }

    @PostMapping("/templates")
    fun criarTemplate(@RequestBody body: Map<String, String>): ResponseEntity<ChecklistTemplateResponse> {
        val template = templateRepository.save(ChecklistTemplate(nome = body["nome"] ?: ""))
        return ResponseEntity.ok(ChecklistTemplateResponse(id = template.id, nome = template.nome, ativo = template.ativo))
    }

    // ==================== ITENS ====================

    @GetMapping("/templates/{templateId}/itens")
    fun listarItens(@PathVariable templateId: String): ResponseEntity<List<ChecklistItemResponse>> {
        return ResponseEntity.ok(itemRepository.findByTemplateId(templateId).map { toItemResponse(it) })
    }

    @GetMapping("/ativo")
    fun buscarTemplateAtivo(): ResponseEntity<ChecklistTemplateResponse> {
        val template = templateRepository.findTemplateAtivo()
            ?: return ResponseEntity.notFound().build()
        val itens = itemRepository.findByTemplateId(template.id.toString()).map { toItemResponse(it) }
        return ResponseEntity.ok(ChecklistTemplateResponse(id = template.id, nome = template.nome, ativo = template.ativo, itens = itens))
    }

    @PostMapping("/itens")
    fun adicionarItem(@RequestBody request: ChecklistItemRequest): ResponseEntity<ChecklistItemResponse> {
        val item = itemRepository.save(ChecklistItem(
            templateId = UUID.fromString(request.templateId),
            descricao = request.descricao,
            obrigatorio = request.obrigatorio,
            ordem = request.ordem
        ))
        return ResponseEntity.ok(toItemResponse(item))
    }

    @PutMapping("/itens/{id}/ativo")
    fun alterarAtivoItem(@PathVariable id: String, @RequestBody body: Map<String, Boolean>): ResponseEntity<Void> {
        itemRepository.alterarAtivo(id, body["ativo"] ?: true)
        return ResponseEntity.ok().build()
    }

    // ==================== RESPOSTAS ====================

    @PostMapping("/respostas")
    fun salvarRespostas(@RequestBody request: ChecklistRespostaRequest): ResponseEntity<Void> {
        request.respostas.forEach { r ->
            respostaRepository.save(ChecklistResposta(
                diarioId = UUID.fromString(request.diarioId),
                itemId = UUID.fromString(r.itemId),
                ok = r.ok,
                observacao = r.observacao
            ))
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/respostas/diario/{diarioId}")
    fun buscarRespostasDiario(@PathVariable diarioId: String): ResponseEntity<List<ChecklistRespostaResponse>> {
        return ResponseEntity.ok(respostaRepository.findByDiarioId(diarioId).map { row ->
            ChecklistRespostaResponse(
                id = UUID.fromString(row[0].toString()),
                itemId = UUID.fromString(row[2].toString()),
                descricao = row[6].toString(),
                obrigatorio = row[7].toString().toBoolean(),
                ok = row[4].toString().toBoolean(),
                observacao = row[3]?.toString(),
                registradoEm = if (row[5] != null)
                    java.time.LocalDateTime.parse(row[5].toString().replace(" ", "T"))
                else null
            )
        })
    }

    private fun toItemResponse(i: ChecklistItem) = ChecklistItemResponse(
        id = i.id, templateId = i.templateId, descricao = i.descricao,
        obrigatorio = i.obrigatorio, ordem = i.ordem, ativo = i.ativo
    )
}