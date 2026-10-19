package com.example.sistemaobras.dto

// Textos das anotações de validação. O tratador de erros monta
// "Campo 'x' <mensagem>." a partir delas.
object MensagensValidacao {
    const val OBRIGATORIO = "é obrigatório"
    const val NAO_NEGATIVO = "não pode ser negativo"
    const val PERFIL = "deve ser gestor ou motorista"
    const val STATUS_MANUTENCAO = "deve ser aberta, em_andamento ou concluida"
    const val STATUS_OCORRENCIA = "deve ser pendente, em_andamento ou resolvida"
    const val MAIOR_QUE_ZERO = "deve ser maior que zero"
    const val MINIMO = "deve ser no mínimo {value}"
    // {max} é preenchido pelo validador com o limite da anotação @Size.
    const val TAMANHO = "deve ter no máximo {max} caracteres"
}
