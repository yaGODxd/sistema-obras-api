package com.example.sistemaobras.exception

import org.springframework.http.HttpStatus

// Erros de negócio. A mensagem vai para o cliente em {"erro": "..."}, então
// deve ser português claro, sem detalhe técnico.
// codigo (opcional) vai no corpo junto com "erro", para o app decidir o que fazer
// sem depender do texto (ex.: deixar a fila offline em espera).
open class ApiException(val status: HttpStatus, mensagem: String, val codigo: String? = null) : RuntimeException(mensagem)

class RegraNegocioException(mensagem: String, codigo: String? = null) : ApiException(HttpStatus.BAD_REQUEST, mensagem, codigo)

// Códigos de erro estáveis (não mudar o texto: o app compara com eles).
object CodigosErro {
    const val PIN_AUSENTE = "PIN_AUSENTE"
    const val DIARIO_PENDENTE_ASSINATURA = "DIARIO_PENDENTE_ASSINATURA"
    const val TURNO_COM_DIARIO_ABERTO = "TURNO_COM_DIARIO_ABERTO"
    const val STATUS_INVALIDO = "STATUS_INVALIDO"
}

class NaoEncontradoException(mensagem: String) : ApiException(HttpStatus.NOT_FOUND, mensagem)

class ConflitoException(mensagem: String) : ApiException(HttpStatus.CONFLICT, mensagem)

class NaoAutorizadoException(mensagem: String) : ApiException(HttpStatus.UNAUTHORIZED, mensagem)

class ProibidoException(mensagem: String) : ApiException(HttpStatus.FORBIDDEN, mensagem)

/**
 * Contrato antigo: a rota respondia SEMPRE este status em qualquer erro
 * (o controller fazia catch de RuntimeException). O tratador de erros mantém
 * esse status para não quebrar o front e o app; só o corpo passa a ter {"erro"}.
 * Remover a anotação de uma rota muda o status de erro dela (precisa de OK).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StatusErroFixo(val value: Int)

// Rotas novas: id inválido responde 400 (e não 500, como nas rotas antigas).
fun uuidValido(texto: String): java.util.UUID =
    try {
        java.util.UUID.fromString(texto)
    } catch (e: IllegalArgumentException) {
        throw RegraNegocioException("Identificador inválido.")
    }
