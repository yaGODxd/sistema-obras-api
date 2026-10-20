package com.example.sistemaobras.service

import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Parte 9: onde os bytes da foto ficam. Hoje: dentro do banco (bytea).
 * Para trocar por armazenamento externo depois, basta outra implementação que envie
 * o arquivo para fora e devolva a chave em caminhoArquivo (conteudo = null), e que
 * leia de volta pela chave. As rotas não mudam, então app e front também não.
 */
interface ArmazenamentoFotos {
    data class Guardado(val conteudo: ByteArray?, val caminhoArquivo: String?)

    fun guardar(id: UUID, bytes: ByteArray): Guardado

    // null quando a foto não tem bytes disponíveis.
    fun ler(id: UUID, conteudo: ByteArray?, caminhoArquivo: String?): ByteArray?
}

@Component
class ArmazenamentoFotosNoBanco : ArmazenamentoFotos {
    override fun guardar(id: UUID, bytes: ByteArray) = ArmazenamentoFotos.Guardado(conteudo = bytes, caminhoArquivo = null)

    override fun ler(id: UUID, conteudo: ByteArray?, caminhoArquivo: String?): ByteArray? = conteudo
}
