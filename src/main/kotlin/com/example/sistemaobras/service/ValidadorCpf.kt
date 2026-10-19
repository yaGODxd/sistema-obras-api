package com.example.sistemaobras.service

// Dígitos verificadores do CPF. Só é usado quando app.validar-cpf-digitos=true.
object ValidadorCpf {

    fun somenteDigitos(cpf: String): String = cpf.filter { it.isDigit() }

    fun valido(cpf: String): Boolean {
        val d = somenteDigitos(cpf).map { it - '0' }
        if (d.size != 11 || d.all { it == d[0] }) return false
        return digito(d, 9) == d[9] && digito(d, 10) == d[10]
    }

    // Calcula o dígito na posição `posicao` a partir dos anteriores.
    private fun digito(d: List<Int>, posicao: Int): Int {
        val soma = (0 until posicao).sumOf { d[it] * (posicao + 1 - it) }
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }
}
