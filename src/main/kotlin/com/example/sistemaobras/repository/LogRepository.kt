package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Log
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface LogRepository : JpaRepository<Log, UUID> {

    fun findByUsuarioLoginOrderByCriadoEmDesc(login: String): List<Log>

    fun findByAcaoOrderByCriadoEmDesc(acao: String): List<Log>

    @Query("""
        SELECT l FROM Log l
        WHERE (:login IS NULL OR l.usuarioLogin = :login)
        AND (:acao IS NULL OR l.acao = :acao)
        AND (:inicio IS NULL OR l.criadoEm >= :inicio)
        AND (:fim IS NULL OR l.criadoEm <= :fim)
        ORDER BY l.criadoEm DESC
    """)
    fun filtrar(
        @Param("login") login: String?,
        @Param("acao") acao: String?,
        @Param("inicio") inicio: LocalDateTime?,
        @Param("fim") fim: LocalDateTime?
    ): List<Log>
}