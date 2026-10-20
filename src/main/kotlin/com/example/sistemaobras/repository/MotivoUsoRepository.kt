package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.MotivoUso
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MotivoUsoRepository : JpaRepository<MotivoUso, UUID> {

    fun findByAtivoTrueOrderByDescricao(): List<MotivoUso>
}
