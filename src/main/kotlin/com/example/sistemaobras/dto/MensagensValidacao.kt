package com.example.sistemaobras.dto

// Textos das anotações de validação. O tratador de erros monta
// "Campo 'x' <mensagem>." a partir delas.
object MensagensValidacao {
    const val OBRIGATORIO = "é obrigatório"
    const val NAO_NEGATIVO = "não pode ser negativo"
    const val PERFIL = "deve ser gestor ou motorista"
}
