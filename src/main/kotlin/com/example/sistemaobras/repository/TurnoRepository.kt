package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Turno
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// Colunas: 0 id, 1 login, 2 status, 3 aberto_em, 4 fechado_em, 5 id_local_abertura, 6 id_local_fechamento
internal const val COLUNAS_TURNO = """
    SELECT t.id, u.login, CAST(t.status AS text), t.aberto_em, t.fechado_em,
           t.id_local_abertura, t.id_local_fechamento
    FROM turnos t
    INNER JOIN usuarios u ON u.id = t.usuario_id
"""

@Repository
interface TurnoRepository : JpaRepository<Turno, UUID> {

    // Parte 6B: aberto_em = hora do evento (celular); aberto_recebido_em = hora do servidor.
    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO turnos (usuario_id, status, aberto_em, aberto_recebido_em, id_local_abertura,
                                horario_suspeito, motivo_suspeita)
            SELECT id, 'aberto', :abertoEm, :recebidoEm, CAST(:idLocal AS uuid), :suspeito, CAST(:motivo AS varchar)
            FROM usuarios
            WHERE login = :login
        """,
        nativeQuery = true
    )
    fun abrirTurno(
        @Param("login") login: String,
        @Param("abertoEm") abertoEm: LocalDateTime,
        @Param("recebidoEm") recebidoEm: LocalDateTime,
        @Param("idLocal") idLocal: String?,
        @Param("suspeito") suspeito: Boolean,
        @Param("motivo") motivo: String?
    )

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE turnos SET
                status = 'fechado',
                fechado_em = :fechadoEm,
                fechado_recebido_em = :recebidoEm,
                id_local_fechamento = CAST(:idLocal AS uuid),
                horario_suspeito = horario_suspeito OR :suspeito,
                motivo_suspeita = CASE
                    WHEN CAST(:motivo AS varchar) IS NULL THEN motivo_suspeita
                    WHEN motivo_suspeita IS NULL THEN CAST(:motivo AS varchar)
                    ELSE LEFT(motivo_suspeita || ',' || CAST(:motivo AS varchar), 255)
                END
            WHERE status = 'aberto'
            AND usuario_id = (SELECT id FROM usuarios WHERE login = :login)
        """,
        nativeQuery = true
    )
    fun fecharTurno(
        @Param("login") login: String,
        @Param("fechadoEm") fechadoEm: LocalDateTime,
        @Param("recebidoEm") recebidoEm: LocalDateTime,
        @Param("idLocal") idLocal: String?,
        @Param("suspeito") suspeito: Boolean,
        @Param("motivo") motivo: String?
    )

    @Query(
        value = """
            SELECT COUNT(*) FROM turnos t
            INNER JOIN usuarios u ON u.id = t.usuario_id
            WHERE u.login = :login AND t.status = 'aberto'
        """,
        nativeQuery = true
    )
    fun temTurnoAberto(@Param("login") login: String): Long

    @Query(value = COLUNAS_TURNO + " WHERE u.login = :login AND t.status = 'aberto' LIMIT 1", nativeQuery = true)
    fun buscarTurnoAberto(@Param("login") login: String): List<Array<Any?>>

    @Query(value = COLUNAS_TURNO + " WHERE t.id_local_abertura = CAST(:idLocal AS uuid)", nativeQuery = true)
    fun buscarPorIdLocalAbertura(@Param("idLocal") idLocal: String): List<Array<Any?>>

    @Query(value = COLUNAS_TURNO + " WHERE t.id_local_fechamento = CAST(:idLocal AS uuid)", nativeQuery = true)
    fun buscarPorIdLocalFechamento(@Param("idLocal") idLocal: String): List<Array<Any?>>
}
