package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.DiarioBordo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Colunas na ordem usada pelo AssinaturaService (índices 0 a 16)
internal const val COLUNAS_DIARIO = """
    SELECT d.id, v.descricao, v.placa, u.login, u.nome_completo, d.veiculo_id,
           d.medidor_inicial, d.medidor_final, d.medidor_percorrido,
           d.destino, d.motivo_uso_id, d.observacao_fechamento, CAST(d.status AS text),
           d.aberto_em, d.fechado_em, d.assinado_em, d.assinatura_hash
    FROM diarios_bordo d
    INNER JOIN usuarios u ON u.id = d.usuario_id
    INNER JOIN veiculos v ON v.id = d.veiculo_id
"""

@Repository
interface AssinaturaRepository : JpaRepository<DiarioBordo, UUID> {

    // ---------- Diários ----------

    @Query(value = COLUNAS_DIARIO + " WHERE d.id = CAST(:id AS uuid)", nativeQuery = true)
    fun buscarDiario(@Param("id") id: String): List<Array<Any?>>

    @Query(
        value = COLUNAS_DIARIO + """
            WHERE u.login = :login
              AND (CAST(:mes AS integer) IS NULL OR EXTRACT(MONTH FROM d.aberto_em) = CAST(:mes AS integer))
              AND (CAST(:ano AS integer) IS NULL OR EXTRACT(YEAR FROM d.aberto_em) = CAST(:ano AS integer))
              AND (CAST(:status AS text) IS NULL OR CAST(d.status AS text) = CAST(:status AS text))
            ORDER BY d.aberto_em DESC
            LIMIT 200
        """,
        nativeQuery = true
    )
    fun historicoDoMotorista(
        @Param("login") login: String,
        @Param("mes") mes: Int?,
        @Param("ano") ano: Int?,
        @Param("status") status: String?
    ): List<Array<Any?>>

    @Query(
        value = """
            SELECT a.id, a.tipo_combustivel, a.litros, a.valor_total, a.posto, a.registrado_em
            FROM abastecimentos a
            WHERE a.diario_id = CAST(:id AS uuid)
            ORDER BY a.registrado_em, a.id
        """,
        nativeQuery = true
    )
    fun abastecimentosDoDiario(@Param("id") id: String): List<Array<Any?>>

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE diarios_bordo
            SET status = 'assinado', assinado_em = LOCALTIMESTAMP, assinatura_hash = :hash
            WHERE id = CAST(:id AS uuid) AND status = 'fechado'
        """,
        nativeQuery = true
    )
    fun assinarDiario(@Param("id") id: String, @Param("hash") hash: String): Int

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE diarios_bordo SET
                destino = COALESCE(CAST(:destino AS text), destino),
                motivo_uso_id = COALESCE(CAST(:motivoUsoId AS uuid), motivo_uso_id),
                observacao_fechamento = COALESCE(CAST(:observacao AS text), observacao_fechamento)
            WHERE id = CAST(:id AS uuid) AND status = 'fechado'
        """,
        nativeQuery = true
    )
    fun editarDiarioFechado(
        @Param("id") id: String,
        @Param("destino") destino: String?,
        @Param("motivoUsoId") motivoUsoId: String?,
        @Param("observacao") observacao: String?
    ): Int

    // ---------- PIN do usuário ----------

    @Query(
        value = "SELECT COUNT(*) FROM usuarios WHERE login = :login AND pin_assinatura_hash IS NOT NULL",
        nativeQuery = true
    )
    fun contarPinDefinido(@Param("login") login: String): Long

    // [0] senha_hash  [1] pin_assinatura_hash  [2] bloqueado?  [3] minutos restantes do bloqueio
    @Query(
        value = """
            SELECT senha_hash, pin_assinatura_hash,
                   (pin_bloqueado_ate IS NOT NULL AND pin_bloqueado_ate > LOCALTIMESTAMP) AS bloqueado,
                   COALESCE(CEIL(EXTRACT(EPOCH FROM (pin_bloqueado_ate - LOCALTIMESTAMP)) / 60), 0) AS min_restantes
            FROM usuarios
            WHERE login = :login
        """,
        nativeQuery = true
    )
    fun dadosPin(@Param("login") login: String): List<Array<Any?>>

    @Query(value = "SELECT pin_tentativas_erradas FROM usuarios WHERE login = :login", nativeQuery = true)
    fun tentativasPin(@Param("login") login: String): Int?

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE usuarios
            SET pin_assinatura_hash = :hash, pin_tentativas_erradas = 0, pin_bloqueado_ate = NULL,
                atualizado_em = NOW()
            WHERE login = :login
        """,
        nativeQuery = true
    )
    fun definirPin(@Param("login") login: String, @Param("hash") hash: String): Int

    @Modifying
    @Transactional
    @Query(
        value = "UPDATE usuarios SET pin_tentativas_erradas = pin_tentativas_erradas + 1 WHERE login = :login",
        nativeQuery = true
    )
    fun incrementarTentativasPin(@Param("login") login: String): Int

    // O INTERVAL de 15 minutos precisa ser igual a AssinaturaService.BLOQUEIO_MINUTOS
    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE usuarios
            SET pin_bloqueado_ate = LOCALTIMESTAMP + INTERVAL '15 minutes', pin_tentativas_erradas = 0
            WHERE login = :login
        """,
        nativeQuery = true
    )
    fun bloquearPin(@Param("login") login: String): Int

    @Modifying
    @Transactional
    @Query(
        value = "UPDATE usuarios SET pin_tentativas_erradas = 0, pin_bloqueado_ate = NULL WHERE login = :login",
        nativeQuery = true
    )
    fun zerarTentativasPin(@Param("login") login: String): Int
}