package com.example.sistemaobras.service

import com.example.sistemaobras.dto.FotoResponse
import com.example.sistemaobras.dto.UsoFotosResponse
import com.example.sistemaobras.exception.ApiException
import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.ProibidoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.exception.uuidValido
import com.example.sistemaobras.repository.FotoRepository
import com.example.sistemaobras.security.ControleAcesso
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Parte 9: envio de foto de comprovação (POST /fotos), separado do evento.
 * O app manda o evento primeiro, recebe o id e depois manda a foto com abastecimentoId/ocorrenciaId.
 */
@Service
class FotoService(
    private val fotoRepository: FotoRepository,
    private val diarioDoEvento: DiarioDoEvento,
    private val armazenamento: ArmazenamentoFotos,
    // A Render gratuita tem pouca memória: no máximo N fotos sendo gravadas ao mesmo tempo.
    @Value("\${app.foto.envios-simultaneos:2}") enviosSimultaneos: Int = 2
) {
    private val vagas = Semaphore(enviosSimultaneos)

    data class EnvioFoto(
        val diarioId: String? = null,
        val tipo: String,
        val ocorrenciaId: String? = null,
        val abastecimentoId: String? = null,
        val idLocal: String? = null,
        val ocorridoEm: String? = null,
        val enviadoEm: String? = null
    )

    companion object {
        val TIPOS = setOf("medidor_abertura", "medidor_fechamento", "ocorrencia", "livre", "cupom_fiscal", "bomba_comboio")
        val TIPOS_DE_ABASTECIMENTO = setOf("cupom_fiscal", "bomba_comboio")

        // Tipo real do arquivo pelos primeiros bytes (não pelo nome). Só JPEG e PNG.
        fun tipoMime(bytes: ByteArray): String? = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ) -> "image/png"
            else -> null
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Transactional
    fun enviar(arquivo: MultipartFile, envio: EnvioFoto): FotoResponse {
        if (!vagas.tryAcquire(2, TimeUnit.SECONDS))
            throw ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "Muitas fotos sendo enviadas agora. Tente de novo em instantes.",
                CodigosErro.FOTO_OCUPADO
            )
        try {
            return gravar(arquivo, envio)
        } finally {
            vagas.release()
        }
    }

    private fun gravar(arquivo: MultipartFile, envio: EnvioFoto): FotoResponse {
        val tipo = envio.tipo.trim()
        if (tipo !in TIPOS)
            throw RegraNegocioException(
                "Tipo de foto inválido. Use: ${TIPOS.joinToString(", ")}.", CodigosErro.TIPO_FOTO_INVALIDO
            )
        val idLocal = envio.idLocal?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        idLocal?.let { fotoRepository.buscarPorIdLocal(it) }?.let { return jaRecebida(it) }

        val ocorrenciaId = envio.ocorrenciaId?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        val abastecimentoId = envio.abastecimentoId?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        val diario = resolverDiario(tipo, envio.diarioId, ocorrenciaId, abastecimentoId)
        diarioDoEvento.conferirNaoAssinado(diario)

        if (arquivo.isEmpty)
            throw RegraNegocioException("O arquivo da foto está vazio.", CodigosErro.ARQUIVO_VAZIO)
        val bytes = arquivo.bytes
        val mime = tipoMime(bytes)
            ?: throw ApiException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A foto precisa ser JPEG ou PNG.", CodigosErro.TIPO_ARQUIVO_INVALIDO
            )

        // tirada_em = hora em que o celular tirou a foto, sempre explícita em UTC.
        val horario = HorarioEvento.resolver(envio.ocorridoEm, null, envio.enviadoEm)
            .conferirJanela(diario.abertoEm, diario.fechadoEm, CodigosErro.FORA_DA_JANELA_DIARIO)

        val id = UUID.randomUUID()
        val guardado = armazenamento.guardar(id, bytes)
        fotoRepository.inserir(
            FotoRepository.NovaFoto(
                id = id, diarioId = diario.id, tipo = tipo, ocorrenciaId = ocorrenciaId, abastecimentoId = abastecimentoId,
                conteudo = guardado.conteudo, caminhoArquivo = guardado.caminhoArquivo, tipoMime = mime,
                tamanhoBytes = bytes.size, hashSha256 = sha256(bytes), idLocal = idLocal,
                tiradaEm = horario.ocorrido, recebidaEm = horario.recebido, registradoPor = ControleAcesso.loginDoToken(),
                desvioSeg = horario.desvioSeg, suspeito = horario.suspeito, motivo = horario.motivoTexto
            )
        )
        return fotoRepository.buscarMetadados(id)?.let(::resposta)
            ?: FotoResponse(
                id, diario.id, tipo, ocorrenciaId, abastecimentoId, horario.ocorrido, horario.recebido, bytes.size, mime,
                sha256(bytes), idLocal, horario.suspeito, horario.motivoTexto
            )
    }

    /**
     * Diário e permissão:
     *  - cupom_fiscal / bomba_comboio: o diário vem do ABASTECIMENTO (no comboio, é o diário do veículo
     *    abastecido). Pode enviar quem registrou o abastecimento ou o dono do diário.
     *  - demais tipos: diarioId obrigatório e o diário tem que ser do motorista do token.
     */
    private fun resolverDiario(
        tipo: String, diarioIdInformado: String?, ocorrenciaId: UUID?, abastecimentoId: UUID?
    ): DiarioDoEvento.Diario {
        if (tipo in TIPOS_DE_ABASTECIMENTO) {
            if (abastecimentoId == null) throw vinculoInvalido("Informe o abastecimentoId para foto de $tipo.")
            if (ocorrenciaId != null) throw vinculoInvalido("Foto de $tipo não se liga a ocorrência.")
            val abastecimento = fotoRepository.abastecimentoDaFoto(abastecimentoId)
                ?: throw NaoEncontradoException("Abastecimento não encontrado.", CodigosErro.VINCULO_NAO_ENCONTRADO)
            if (!diarioIdInformado.isNullOrBlank() && uuidValido(diarioIdInformado) != abastecimento.diarioId)
                throw vinculoInvalido("O diário informado não é o do abastecimento.")
            val diario = diarioDoEvento.porId(abastecimento.diarioId.toString())
            if (ControleAcesso.ehMotorista()) {
                val quem = ControleAcesso.loginDoToken()
                if (quem != abastecimento.registradoPor && quem != diario.dono)
                    throw ProibidoException("Esta foto é de um abastecimento de outro motorista.", CodigosErro.DIARIO_DE_OUTRO_MOTORISTA)
            }
            return diario
        }

        if (diarioIdInformado.isNullOrBlank()) throw vinculoInvalido("Informe o diarioId da foto.")
        val diario = diarioDoEvento.porId(diarioIdInformado)
        diarioDoEvento.conferirDono(diario)
        when (tipo) {
            "ocorrencia" -> {
                if (ocorrenciaId == null) throw vinculoInvalido("Informe o ocorrenciaId para foto de ocorrência.")
                if (abastecimentoId != null) throw vinculoInvalido("Foto de ocorrência não se liga a abastecimento.")
            }
            "medidor_abertura", "medidor_fechamento" ->
                if (ocorrenciaId != null || abastecimentoId != null) throw vinculoInvalido("Foto do medidor se liga só ao diário.")
        }
        ocorrenciaId?.let {
            val doDiario = fotoRepository.diarioDaOcorrencia(it)
                ?: throw NaoEncontradoException("Ocorrência não encontrada.", CodigosErro.VINCULO_NAO_ENCONTRADO)
            if (doDiario != diario.id) throw vinculoInvalido("A ocorrência não é deste diário.")
        }
        abastecimentoId?.let {
            val abastecimento = fotoRepository.abastecimentoDaFoto(it)
                ?: throw NaoEncontradoException("Abastecimento não encontrado.", CodigosErro.VINCULO_NAO_ENCONTRADO)
            if (abastecimento.diarioId != diario.id) throw vinculoInvalido("O abastecimento não é deste diário.")
        }
        return diario
    }

    // Reenvio com o mesmo idLocal: devolve a foto já gravada se for de quem enviou ou do dono do diário.
    private fun jaRecebida(foto: FotoRepository.FotoMetadados): FotoResponse {
        val quem = ControleAcesso.loginDoToken()
        if (quem != null && quem != foto.registradoPor) {
            val dono = diarioDoEvento.porIdSeExistir(foto.diarioId.toString())?.dono
            if (quem != dono)
                throw ConflitoException("Este idLocal já foi usado em outro registro.", CodigosErro.ID_LOCAL_EM_USO)
        }
        return resposta(foto)
    }

    // ===================== Etapa 3: leitura =====================

    data class Imagem(val bytes: ByteArray, val tipoMime: String, val hashSha256: String?)

    // GET /fotos/{id}: a imagem. Gestor vê todas; motorista vê as do próprio diário e as que ele enviou (comboio).
    fun lerImagem(id: String): Imagem {
        val uuid = uuidValido(id)
        val foto = fotoRepository.buscarMetadados(uuid) ?: throw fotoNaoEncontrada()
        conferirLeitura(foto)
        val guardado = fotoRepository.lerConteudo(uuid) ?: throw fotoNaoEncontrada()
        val bytes = armazenamento.ler(uuid, guardado.conteudo, guardado.caminhoArquivo) ?: throw fotoNaoEncontrada()
        return Imagem(bytes, foto.tipoMime ?: "application/octet-stream", foto.hashSha256)
    }

    // GET /diarios/{id}/fotos: só os metadados (nunca os bytes). Motorista: só o próprio diário.
    fun listarDoDiario(diarioId: String): List<FotoResponse> {
        val diario = diarioDoEvento.porId(diarioId)
        diarioDoEvento.conferirDono(diario)
        return fotoRepository.listarDoDiario(diario.id).map(::resposta)
    }

    // GET /fotos/uso (gestor): espaço ocupado, para decidir a hora de ir para armazenamento externo.
    fun uso(): UsoFotosResponse {
        val u = fotoRepository.uso()
        fun mb(bytes: Long) = Math.round(bytes / 1024.0 / 1024.0 * 10) / 10.0
        return UsoFotosResponse(
            quantidadeFotos = u.quantidade, bytesFotos = u.bytesFotos, megabytesFotos = mb(u.bytesFotos),
            bytesTabelaFotos = u.bytesTabela, megabytesTabelaFotos = mb(u.bytesTabela),
            bytesBanco = u.bytesBanco, megabytesBanco = mb(u.bytesBanco)
        )
    }

    private fun conferirLeitura(foto: FotoRepository.FotoMetadados) {
        if (!ControleAcesso.ehMotorista()) return
        val quem = ControleAcesso.loginDoToken()
        if (quem == foto.registradoPor) return
        if (quem != diarioDoEvento.porIdSeExistir(foto.diarioId.toString())?.dono)
            throw ProibidoException("Esta foto é de outro motorista.", CodigosErro.DIARIO_DE_OUTRO_MOTORISTA)
    }

    private fun fotoNaoEncontrada() = NaoEncontradoException("Foto não encontrada.", CodigosErro.FOTO_NAO_ENCONTRADA)

    private fun vinculoInvalido(mensagem: String) = RegraNegocioException(mensagem, CodigosErro.VINCULO_INVALIDO)

    private fun resposta(f: FotoRepository.FotoMetadados) = FotoResponse(
        id = f.id, diarioId = f.diarioId, tipo = f.tipo, ocorrenciaId = f.ocorrenciaId, abastecimentoId = f.abastecimentoId,
        tiradaEm = f.tiradaEm, recebidaEm = f.recebidaEm, tamanhoBytes = f.tamanhoBytes, tipoMime = f.tipoMime,
        hashSha256 = f.hashSha256, idLocal = f.idLocal, horarioSuspeito = f.horarioSuspeito, motivoSuspeita = f.motivoSuspeita
    )
}
