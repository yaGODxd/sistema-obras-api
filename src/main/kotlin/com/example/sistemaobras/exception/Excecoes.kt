package com.example.sistemaobras.exception

import org.springframework.http.HttpStatus

// Erros de negócio. A mensagem vai para o cliente em {"erro": "..."}, então
// deve ser português claro, sem detalhe técnico.
open class ApiException(val status: HttpStatus, mensagem: String) : RuntimeException(mensagem)

class RegraNegocioException(mensagem: String) : ApiException(HttpStatus.BAD_REQUEST, mensagem)

class NaoEncontradoException(mensagem: String) : ApiException(HttpStatus.NOT_FOUND, mensagem)

class ConflitoException(mensagem: String) : ApiException(HttpStatus.CONFLICT, mensagem)

class NaoAutorizadoException(mensagem: String) : ApiException(HttpStatus.UNAUTHORIZED, mensagem)

/**
 * Contrato antigo: a rota respondia SEMPRE este status em qualquer erro
 * (o controller fazia catch de RuntimeException). O tratador de erros mantém
 * esse status para não quebrar o front e o app; só o corpo passa a ter {"erro"}.
 * Remover a anotação de uma rota muda o status de erro dela (precisa de OK).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StatusErroFixo(val value: Int)
