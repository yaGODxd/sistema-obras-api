package com.example.sistemaobras.service

import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.RegraNegocioException
import java.util.Base64

// Foto de perfil (base64 no banco). Normaliza na escrita: celulares antigos mandam base64
// puro com quebras de linha e sem prefixo; grava sempre "data:image/<tipo>;base64,<base64>".
object FotoPerfil {
    private val PREFIXO = Regex("^data:image/(jpeg|jpg|png);base64,", RegexOption.IGNORE_CASE)

    // null = não alterar a foto (vazia ou ausente).
    fun normalizar(texto: String?, maxBytes: Long): String? {
        if (texto.isNullOrBlank()) return null
        val limpo = texto.replace(Regex("\\s"), "")

        val base64 = when {
            PREFIXO.containsMatchIn(limpo) -> limpo.replaceFirst(PREFIXO, "")
            limpo.startsWith("data:", ignoreCase = true) -> throw invalida()
            else -> limpo
        }
        // Barra o texto enorme antes de decodificar (4 caracteres = 3 bytes).
        if (base64.length / 4 * 3 > maxBytes + 3) throw grandeDemais(maxBytes)

        // Decoder estrito: qualquer caractere fora do base64 no meio do texto é recusado.
        val bytes = try { Base64.getDecoder().decode(base64) } catch (e: IllegalArgumentException) { throw invalida() }
        if (bytes.size > maxBytes) throw grandeDemais(maxBytes)

        // O tipo vem dos bytes, não do prefixo enviado.
        val tipo = FotoService.tipoMime(bytes) ?: throw invalida()
        return "data:$tipo;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    private fun invalida() =
        RegraNegocioException("A foto de perfil precisa ser uma imagem JPEG ou PNG.", CodigosErro.FOTO_PERFIL_INVALIDA)

    private fun grandeDemais(maxBytes: Long) =
        RegraNegocioException("A foto de perfil passou do tamanho máximo (${maxBytes / 1024} KB).", CodigosErro.FOTO_PERFIL_GRANDE_DEMAIS)
}
