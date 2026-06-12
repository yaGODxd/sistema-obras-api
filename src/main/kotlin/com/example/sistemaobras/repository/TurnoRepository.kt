package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Turno
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface TurnoRepository : JpaRepository<Turno, UUID> {

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO turnos (usuario_id, status, aberto_em)
            SELECT id, 'aberto', NOW()
            FROM usuarios
            WHERE login = :login
        """,
        nativeQuery = true
    )
    fun abrirTurno(
        @org.springframework.data.repository.query.Param("login") login: String
    )

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE turnos SET status = 'fechado', fechado_em = NOW()
            WHERE status = 'aberto'
            AND usuario_id = (SELECT id FROM usuarios WHERE login = :login)
        """,
        nativeQuery = true
    )
    fun fecharTurno(
        @org.springframework.data.repository.query.Param("login") login: String
    )

    @Query(
        value = """
            SELECT COUNT(*) FROM turnos t
            INNER JOIN usuarios u ON u.id = t.usuario_id
            WHERE u.login = :login AND t.status = 'aberto'
        """,
        nativeQuery = true
    )
    fun temTurnoAberto(
        @org.springframework.data.repository.query.Param("login") login: String
    ): Long
}