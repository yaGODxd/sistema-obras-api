package com.example.sistemaobras.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.core.JacksonException
import java.sql.SQLException
import java.time.format.DateTimeParseException

/**
 * Todas as respostas de erro saem no formato {"erro": "mensagem em português"}.
 * O status segue a regra de compatibilidade:
 *  - erros do próprio Spring (JSON inválido, parâmetro errado, rota inexistente): status do Spring, como antes;
 *  - rota com @StatusErroFixo: o status antigo da rota;
 *  - senão: o status da ApiException, ou 500 para erro inesperado.
 */
@RestControllerAdvice
class TratadorDeErros : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(TratadorDeErros::class.java)

    @ExceptionHandler(ApiException::class)
    fun tratarApi(e: ApiException, request: HttpServletRequest): ResponseEntity<Map<String, String>> {
        return responder(statusFixo(request) ?: e.status, e.message ?: MENSAGEM_ERRO_INTERNO, e.codigo)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun tratarIntegridade(e: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<Map<String, String>> {
        val sql = causaSql(e)
        log.warn("Banco recusou a gravação em {} {}: sqlState={} {}",
            request.method, request.requestURI, sql?.sqlState, sql?.message)
        return responder(statusFixo(request) ?: HttpStatus.INTERNAL_SERVER_ERROR, mensagemIntegridade(sql))
    }

    @ExceptionHandler(Exception::class)
    fun tratarInesperado(e: Exception, request: HttpServletRequest): ResponseEntity<Map<String, String>> {
        val mensagem = if (isUuidInvalido(e)) {
            log.warn("Identificador inválido em {} {}: {}", request.method, request.requestURI, e.message)
            "Identificador inválido."
        } else if (e is DateTimeParseException) {
            log.warn("Data inválida em {} {}: {}", request.method, request.requestURI, e.message)
            "Data inválida. Use o formato AAAA-MM-DD."
        } else {
            log.error("Erro inesperado em {} {}", request.method, request.requestURI, e)
            MENSAGEM_ERRO_INTERNO
        }
        return responder(statusFixo(request) ?: HttpStatus.INTERNAL_SERVER_ERROR, mensagem)
    }

    // Erros tratados pelo próprio Spring MVC: mantém o status dele, troca só o corpo.
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        if (statusCode.is5xxServerError) log.error("Erro interno do Spring MVC", ex)
        return ResponseEntity.status(statusCode).headers(headers).body(mapOf("erro" to mensagemSpring(ex, statusCode)))
    }

    // "codigo" só aparece quando a exceção tem um (campo a mais; quem lê só "erro" não muda).
    private fun responder(status: HttpStatusCode, mensagem: String, codigo: String? = null): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).body(if (codigo == null) mapOf("erro" to mensagem) else mapOf("erro" to mensagem, "codigo" to codigo))

    private fun statusFixo(request: HttpServletRequest): HttpStatusCode? {
        val handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) as? HandlerMethod
        return handler?.getMethodAnnotation(StatusErroFixo::class.java)?.let { HttpStatusCode.valueOf(it.value) }
    }

    private fun mensagemSpring(ex: Exception, status: HttpStatusCode): String = when (ex) {
        is HttpMessageNotReadableException -> {
            val campo = (ex.cause as? JacksonException)?.path
                ?.mapNotNull { it.propertyName }?.joinToString(".")
            if (!campo.isNullOrBlank()) "Campo ausente ou com valor inválido: $campo."
            else "Corpo da requisição inválido ou ausente."
        }
        is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors.firstOrNull()
            ?.let { "Campo '${it.field}' ${it.defaultMessage}." } ?: "Dados inválidos."
        is MissingServletRequestParameterException -> "Parâmetro obrigatório ausente: ${ex.parameterName}."
        is TypeMismatchException -> "Valor inválido para '${ex.propertyName ?: "parâmetro"}'."
        is NoResourceFoundException -> "Rota não encontrada."
        is HttpRequestMethodNotSupportedException -> "Método HTTP não permitido nesta rota."
        is HttpMediaTypeNotSupportedException -> "Tipo de conteúdo não suportado. Envie JSON."
        else -> if (status.is5xxServerError) MENSAGEM_ERRO_INTERNO else "Requisição inválida."
    }

    private fun mensagemIntegridade(sql: SQLException?): String {
        val estado = sql?.sqlState ?: ""
        val texto = sql?.message?.lowercase() ?: ""
        return when {
            estado == "23505" && texto.contains("login") -> "Já existe um usuário com este login."
            estado == "23505" && texto.contains("placa") -> "Já existe um veículo com esta placa."
            estado == "23505" && texto.contains("cpf") -> "Já existe um cadastro com este CPF."
            estado == "23505" -> "Já existe um registro com estes dados."
            estado == "23503" -> "Registro relacionado não encontrado ou ainda em uso."
            estado == "23502" -> "Campo obrigatório não informado."
            estado == "22001" -> "Algum texto passou do tamanho máximo permitido."
            estado.startsWith("22") -> "Valor inválido em algum campo."
            else -> "Não foi possível gravar os dados."
        }
    }

    private fun causaSql(e: Throwable): SQLException? =
        generateSequence(e) { it.cause }.filterIsInstance<SQLException>().firstOrNull()

    // UUID.fromString com texto inválido (id na URL ou no corpo).
    private fun isUuidInvalido(e: Exception): Boolean =
        e is IllegalArgumentException && e.stackTrace.take(5).any { it.className == "java.util.UUID" }

    companion object {
        const val MENSAGEM_ERRO_INTERNO = "Erro interno. Tente novamente."
    }
}
