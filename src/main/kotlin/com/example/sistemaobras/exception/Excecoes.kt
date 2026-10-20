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

    // Parte 6B (uso offline). Recusas:
    const val ID_INVALIDO = "ID_INVALIDO"
    const val HORARIO_INVALIDO = "HORARIO_INVALIDO"
    const val HORARIO_FUTURO = "HORARIO_FUTURO"             // também usado como motivo de suspeita
    const val DIARIO_NAO_ENCONTRADO = "DIARIO_NAO_ENCONTRADO"
    const val SEM_DIARIO_ABERTO = "SEM_DIARIO_ABERTO"
    const val DIARIO_DE_OUTRO_MOTORISTA = "DIARIO_DE_OUTRO_MOTORISTA"
    const val DIARIO_ASSINADO = "DIARIO_ASSINADO"
    const val DIARIO_JA_FECHADO = "DIARIO_JA_FECHADO"
    const val ID_LOCAL_EM_USO = "ID_LOCAL_EM_USO"
    const val REENVIO_SIMULTANEO = "REENVIO_SIMULTANEO"
    const val LOTE_GRANDE_DEMAIS = "LOTE_GRANDE_DEMAIS"
    const val PLACA_EM_USO = "PLACA_EM_USO"
    const val PLACA_DE_VEICULO_INATIVO = "PLACA_DE_VEICULO_INATIVO"

    // Parte 9 (fotos):
    const val FOTO_GRANDE_DEMAIS = "FOTO_GRANDE_DEMAIS"         // 413
    const val TIPO_ARQUIVO_INVALIDO = "TIPO_ARQUIVO_INVALIDO"   // 415 (só JPEG e PNG, conferido pelos bytes)
    const val FOTO_OCUPADO = "FOTO_OCUPADO"                     // 503 (tentar de novo)
    const val ARQUIVO_VAZIO = "ARQUIVO_VAZIO"
    const val TIPO_FOTO_INVALIDO = "TIPO_FOTO_INVALIDO"
    const val VINCULO_INVALIDO = "VINCULO_INVALIDO"
    const val VINCULO_NAO_ENCONTRADO = "VINCULO_NAO_ENCONTRADO"
    const val FOTO_NAO_ENCONTRADA = "FOTO_NAO_ENCONTRADA"

    // Foto de perfil (base64 em usuarios.foto_perfil):
    const val FOTO_PERFIL_INVALIDA = "FOTO_PERFIL_INVALIDA"             // 400 (só JPEG e PNG, base64 válido)
    const val FOTO_PERFIL_GRANDE_DEMAIS = "FOTO_PERFIL_GRANDE_DEMAIS"   // 400 (APP_FOTO_PERFIL_MAX)

    // Parte 6B: motivos de horário suspeito (o registro é aceito e marcado).
    const val HORARIO_ANTIGO = "HORARIO_ANTIGO"
    const val DESVIO_RELOGIO = "DESVIO_RELOGIO"
    const val FORA_DA_JANELA_DIARIO = "FORA_DA_JANELA_DIARIO"
    const val FORA_DA_JANELA_TURNO = "FORA_DA_JANELA_TURNO"
    const val COMBOIO_SEM_DIARIO = "COMBOIO_SEM_DIARIO"
}

class NaoEncontradoException(mensagem: String, codigo: String? = null) : ApiException(HttpStatus.NOT_FOUND, mensagem, codigo)

class ConflitoException(mensagem: String, codigo: String? = null) : ApiException(HttpStatus.CONFLICT, mensagem, codigo)

class NaoAutorizadoException(mensagem: String) : ApiException(HttpStatus.UNAUTHORIZED, mensagem)

class ProibidoException(mensagem: String, codigo: String? = null) : ApiException(HttpStatus.FORBIDDEN, mensagem, codigo)

// 422: o pedido está bem formado, mas o dado não pode ser aceito (ex.: horário muito no futuro).
class NaoProcessavelException(mensagem: String, codigo: String? = null) : ApiException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem, codigo)

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
        throw RegraNegocioException("Identificador inválido.", CodigosErro.ID_INVALIDO)
    }
