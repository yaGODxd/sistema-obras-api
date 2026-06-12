package com.example.sistemaobras.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import java.time.Instant

@Service
class JwtService {

    @Value("\${jwt.secret}")
    lateinit var secret: String

    @Value("\${jwt.expiration}")
    var expiration: Long = 86400000

    fun gerarToken(login: String, perfil: String): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())

        val now = Instant.now().toEpochMilli()
        val exp = now + expiration

        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"$login","perfil":"$perfil","iat":$now,"exp":$exp}""".toByteArray())

        val signature = assinar("$header.$payload")

        return "$header.$payload.$signature"
    }

    fun validarToken(token: String): Boolean {
        return try {
            val partes = token.split(".")
            if (partes.size != 3) return false
            val assinaturaEsperada = assinar("${partes[0]}.${partes[1]}")
            if (partes[2] != assinaturaEsperada) return false
            val payload = String(Base64.getUrlDecoder().decode(partes[1]))
            val exp = Regex("\"exp\":(\\d+)").find(payload)?.groupValues?.get(1)?.toLong() ?: return false
            Instant.now().toEpochMilli() < exp
        } catch (e: Exception) {
            false
        }
    }

    fun extrairLogin(token: String): String? {
        return try {
            val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
            Regex("\"sub\":\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun assinar(dados: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(dados.toByteArray()))
    }
}