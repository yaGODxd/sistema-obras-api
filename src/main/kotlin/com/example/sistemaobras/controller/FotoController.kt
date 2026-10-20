package com.example.sistemaobras.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import com.example.sistemaobras.dto.FotoResponse
import com.example.sistemaobras.dto.UsoFotosResponse
import com.example.sistemaobras.service.FotoService
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.concurrent.TimeUnit

// Caminhos completos em cada rota (sem @RequestMapping na classe) porque a lista fica em /diarios/{id}/fotos.
@Tag(name = "Fotos", description = "Fotos de comprovação guardadas no banco (Parte 9). Envio separado do evento.")
@RestController
class FotoController(
    private val fotoService: FotoService
) {
    // Rota nova (Parte 9): 200 gravou ou reenvio (mesmo idLocal); 400 dado/vínculo inválido; 403 diário/abastecimento
    // de outro motorista; 404 diário/ocorrência/abastecimento não existe; 409 DIARIO_ASSINADO, ID_LOCAL_EM_USO,
    // REENVIO_SIMULTANEO; 413 FOTO_GRANDE_DEMAIS; 415 TIPO_ARQUIVO_INVALIDO; 422 HORARIO_FUTURO; 503 FOTO_OCUPADO.
    @Operation(
        summary = "Enviar foto de comprovação (motorista)",
        description = "multipart/form-data com o arquivo (JPEG ou PNG, até APP_FOTO_MAX_ARQUIVO, padrão 5 MB). " +
            "tipo: medidor_abertura, medidor_fechamento, ocorrencia (com ocorrenciaId), livre, cupom_fiscal ou " +
            "bomba_comboio (com abastecimentoId; o diário vem do abastecimento). Mesmo idLocal devolve a foto já gravada."
    )
    @PostMapping("/fotos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun enviar(
        @RequestPart("arquivo") arquivo: MultipartFile,
        @RequestParam tipo: String,
        @RequestParam(required = false) diarioId: String?,
        @RequestParam(required = false) ocorrenciaId: String?,
        @RequestParam(required = false) abastecimentoId: String?,
        @RequestParam(required = false) idLocal: String?,
        @RequestParam(required = false) ocorridoEm: String?,
        @RequestParam(required = false) enviadoEm: String?
    ): ResponseEntity<FotoResponse> {
        val envio = FotoService.EnvioFoto(diarioId, tipo, ocorrenciaId, abastecimentoId, idLocal, ocorridoEm, enviadoEm)
        return ResponseEntity.ok(fotoService.enviar(arquivo, envio))
    }

    @Operation(
        summary = "Espaço usado pelas fotos (gestor)",
        description = "Quantidade, bytes das fotos, tamanho da tabela e do banco inteiro."
    )
    @GetMapping("/fotos/uso")
    fun uso(): ResponseEntity<UsoFotosResponse> = ResponseEntity.ok(fotoService.uso())

    // A imagem em si. Cache privado de 1 dia; ETag = sha256 (a foto nunca muda), então If-None-Match responde 304.
    @Operation(
        summary = "Imagem da foto",
        description = "Devolve a imagem (image/jpeg ou image/png). No navegador, buscar com o token e mostrar como blob " +
            "(o <img src> não manda o Authorization). 400 id inválido, 403 de outro motorista, 404 não existe."
    )
    @GetMapping("/fotos/{id}")
    fun imagem(
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) seNaoMudou: String?
    ): ResponseEntity<ByteArray> {
        val imagem = fotoService.lerImagem(id)
        val etag = imagem.hashSha256?.let { "\"$it\"" }
        val cache = CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate()
        if (etag != null && seNaoMudou == etag)
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build()
        val resposta = ResponseEntity.ok().contentType(MediaType.parseMediaType(imagem.tipoMime)).cacheControl(cache)
        etag?.let { resposta.eTag(it) }
        return resposta.body(imagem.bytes)
    }

    @Operation(
        summary = "Fotos de um diário (só metadados)",
        description = "Lista sem os bytes (a imagem sai em GET /fotos/{id}). Motorista só vê o próprio diário."
    )
    @GetMapping("/diarios/{id}/fotos")
    fun listarDoDiario(@PathVariable id: String): ResponseEntity<List<FotoResponse>> =
        ResponseEntity.ok(fotoService.listarDoDiario(id))
}
