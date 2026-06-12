package com.example.sistemaobras.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class PerfilUsuarioConverter : AttributeConverter<PerfilUsuario, String> {
    override fun convertToDatabaseColumn(attribute: PerfilUsuario?): String? {
        return attribute?.name
    }

    override fun convertToEntityAttribute(dbData: String?): PerfilUsuario? {
        return dbData?.let { PerfilUsuario.valueOf(it) }
    }
}