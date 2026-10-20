package com.example.sistemaobras.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory

// Leitura tolerante (decisão 19/10): um perfil que o código não conhece não derruba
// GET /usuarios; vira "desconhecido", que não tem permissão nenhuma com a matriz ligada.
@Converter(autoApply = true)
class PerfilUsuarioConverter : AttributeConverter<PerfilUsuario, String> {
    private val log = LoggerFactory.getLogger(PerfilUsuarioConverter::class.java)

    override fun convertToDatabaseColumn(attribute: PerfilUsuario?): String? {
        return attribute?.name
    }

    override fun convertToEntityAttribute(dbData: String?): PerfilUsuario? {
        return dbData?.let { valor ->
            PerfilUsuario.entries.firstOrNull { it.name == valor } ?: run {
                log.warn("Perfil de usuário desconhecido no banco: {}", valor)
                PerfilUsuario.desconhecido
            }
        }
    }
}
