package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Abastecimento
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface AbastecimentoRepository : JpaRepository<Abastecimento, UUID> {

    // Parte 6B: o diário já vem resolvido pelo service (aberto agora, por id ou pelo
    // veículo no horário do evento). registrado_em = hora do evento; recebido_em = servidor.
    @Modifying
    @Transactional
    @Query(
        value = """
    INSERT INTO abastecimentos (id, diario_id, tipo_combustivel, litros, valor_total, posto, veiculo_abastecido_id,
                                registrado_em, recebido_em, id_local, desvio_relogio_seg, horario_suspeito,
                                motivo_suspeita, registrado_por)
    VALUES (
        CAST(:id AS uuid), CAST(:diarioId AS uuid), :tipoCombustivel, :litros, :valorTotal, CAST(:posto AS varchar),
        CAST(:veiculoAbastecidoId AS uuid),
        :registradoEm, :recebidoEm, CAST(:idLocal AS uuid), CAST(:desvio AS integer), :suspeito,
        CAST(:motivo AS varchar),
        (SELECT u.id FROM usuarios u WHERE u.login = CAST(:registradoPor AS varchar))
    )
""",
        nativeQuery = true
    )
    fun inserirAbastecimento(
        @Param("id") id: String,
        @Param("diarioId") diarioId: String,
        @Param("tipoCombustivel") tipoCombustivel: String,
        @Param("litros") litros: Double,
        @Param("valorTotal") valorTotal: Double,
        @Param("posto") posto: String?,
        @Param("veiculoAbastecidoId") veiculoAbastecidoId: String?,
        @Param("registradoEm") registradoEm: java.time.LocalDateTime,
        @Param("recebidoEm") recebidoEm: java.time.LocalDateTime,
        @Param("idLocal") idLocal: String?,
        @Param("desvio") desvio: Int?,
        @Param("suspeito") suspeito: Boolean,
        @Param("motivo") motivo: String?,
        @Param("registradoPor") registradoPor: String?
    )

    // Reenvio (Parte 6B): [0] id, [1] login do dono do diário, [2] login de quem registrou (pode ser nulo).
    @Query(
        value = """
        SELECT a.id, u.login, rp.login
        FROM abastecimentos a
        INNER JOIN diarios_bordo d ON d.id = a.diario_id
        INNER JOIN usuarios u ON u.id = d.usuario_id
        LEFT JOIN usuarios rp ON rp.id = a.registrado_por
        WHERE a.id_local = CAST(:idLocal AS uuid)
    """,
        nativeQuery = true
    )
    fun buscarPorIdLocal(@Param("idLocal") idLocal: String): List<Array<Any?>>

    @Query(
        value = """
            SELECT a.* FROM abastecimentos a
            INNER JOIN diarios_bordo d ON d.id = a.diario_id
            INNER JOIN usuarios u ON u.id = d.usuario_id
            WHERE u.login = :login AND d.status = 'aberto'
            ORDER BY a.registrado_em DESC
        """,
        nativeQuery = true
    )
    fun findByDiarioAberto(
        @org.springframework.data.repository.query.Param("login") login: String
    ): List<Abastecimento>

    @Query(
        value = "SELECT a.* FROM abastecimentos a WHERE a.diario_id = CAST(:diarioId AS uuid) ORDER BY a.registrado_em DESC",
        nativeQuery = true
    )
    fun findByDiarioId(
        @org.springframework.data.repository.query.Param("diarioId") diarioId: String
    ): List<Abastecimento>
    @Query(
        value = """
        SELECT
            -- Colunas explícitas (índices 0-8 lidos por posição no AbastecimentoService).
            -- Não usar a.* (coluna nova na tabela deslocaria os índices).
            a.id, a.diario_id, a.tipo_combustivel, a.litros, a.valor_total, a.posto,
            a.sincronizado, a.registrado_em, a.veiculo_abastecido_id,
            v.id as veiculo_id,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.login as motorista_login,
            u.nome_completo as motorista_nome,
            va.descricao as veiculo_abastecido_descricao
        FROM abastecimentos a
        INNER JOIN diarios_bordo d ON d.id = a.diario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        INNER JOIN usuarios u ON u.id = d.usuario_id
        LEFT JOIN veiculos va ON va.id = a.veiculo_abastecido_id
        ORDER BY a.registrado_em DESC
    """,
        nativeQuery = true
    )
    fun findTodos(): List<Array<Any>>

    @Query(
        value = """
        SELECT
            -- Colunas explícitas (índices 0-8 lidos por posição no AbastecimentoService).
            -- Não usar a.* (coluna nova na tabela deslocaria os índices).
            a.id, a.diario_id, a.tipo_combustivel, a.litros, a.valor_total, a.posto,
            a.sincronizado, a.registrado_em, a.veiculo_abastecido_id,
            v.id as veiculo_id,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.login as motorista_login,
            u.nome_completo as motorista_nome,
            va.descricao as veiculo_abastecido_descricao
        FROM abastecimentos a
        INNER JOIN diarios_bordo d ON d.id = a.diario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        INNER JOIN usuarios u ON u.id = d.usuario_id
        LEFT JOIN veiculos va ON va.id = a.veiculo_abastecido_id
        WHERE v.id = CAST(:veiculoId AS uuid)
           OR a.veiculo_abastecido_id = CAST(:veiculoId AS uuid)
        ORDER BY a.registrado_em DESC
    """,
        nativeQuery = true
    )
    fun findByVeiculo(
        @org.springframework.data.repository.query.Param("veiculoId") veiculoId: String
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT
            -- Colunas explícitas (índices 0-8 lidos por posição no AbastecimentoService).
            -- Não usar a.* (coluna nova na tabela deslocaria os índices).
            a.id, a.diario_id, a.tipo_combustivel, a.litros, a.valor_total, a.posto,
            a.sincronizado, a.registrado_em, a.veiculo_abastecido_id,
            v.id as veiculo_id,
            v.descricao as veiculo_descricao,
            v.placa as veiculo_placa,
            u.login as motorista_login,
            u.nome_completo as motorista_nome,
            va.descricao as veiculo_abastecido_descricao
        FROM abastecimentos a
        INNER JOIN diarios_bordo d ON d.id = a.diario_id
        INNER JOIN veiculos v ON v.id = d.veiculo_id
        INNER JOIN usuarios u ON u.id = d.usuario_id
        LEFT JOIN veiculos va ON va.id = a.veiculo_abastecido_id
        WHERE u.login = :login
        ORDER BY a.registrado_em DESC
    """,
        nativeQuery = true
    )
    fun findByMotorista(
        @org.springframework.data.repository.query.Param("login") login: String
    ): List<Array<Any>>
}