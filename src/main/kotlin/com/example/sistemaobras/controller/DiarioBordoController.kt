package com.example.sistemaobras.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "Diários de bordo", description = "Abrir, fechar (por login ou por id), listas e detalhe.")
@RestController
@RequestMapping("/diarios")
class DiarioBordoController(
    private val diarioService: DiarioBordoService
) {

    @Operation(
        summary = "Abrir diário (motorista)",
        description = "Exige turno aberto, PIN cadastrado e nenhum diário pendente de assinatura (códigos PIN_AUSENTE, " +
            "DIARIO_PENDENTE_ASSINATURA). ocorridoEm = hora real da abertura; idLocal torna o reenvio seguro. Erros: 400."
    )
    @PostMapping("/abrir")
    @StatusErroFixo(400)
    fun abrirDiario(@Valid @RequestBody request: AbrirDiarioRequest): ResponseEntity<DiarioResponse> {
        return ResponseEntity.ok(diarioService.abrirDiario(request.copy(loginMotorista = ControleAcesso.loginEfetivo(request.loginMotorista))))
    }

    @Operation(
        summary = "Fechar o diário aberto do motorista (rota antiga)",
        description = "Medidor final não pode ser menor que o inicial. Aceita ocorridoEm e idLocal. Erros: 400 (com codigo)."
    )
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
    @Operation(
        summary = "Fechar diário pelo id (uso offline)",
        description = "fechado_em = ocorridoEm (hora real). Mesmo idLocal devolve o resultado já gravado. " +
            "403 diário de outro motorista; 404 não existe; 409 DIARIO_JA_FECHADO ou ID_LOCAL_EM_USO; 422 HORARIO_FUTURO."
    )
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