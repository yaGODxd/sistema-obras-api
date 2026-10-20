package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.ChecklistResposta
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChecklistRespostaRepository : JpaRepository<ChecklistResposta, UUID> {

    // Parte 6B: o índice único (diario_id, item_id) vira a idempotência do checklist.
    // Reenvio atualiza a resposta; um reenvio mais ANTIGO não desfaz uma resposta mais nova.
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO checklist_respostas (diario_id, item_id, ok, observacao, registrado_em, recebido_em)
        VALUES (CAST(:diarioId AS uuid), CAST(:itemId AS uuid), :ok, CAST(:observacao AS varchar), :registradoEm, :recebidoEm)
        ON CONFLICT (diario_id, item_id) DO UPDATE
            SET ok = EXCLUDED.ok,
                observacao = EXCLUDED.observacao,
                registrado_em = EXCLUDED.registrado_em,
                recebido_em = EXCLUDED.recebido_em
            WHERE EXCLUDED.registrado_em >= checklist_respostas.registrado_em
    """, nativeQuery = true)
    fun gravarResposta(
        @Param("diarioId") diarioId: String,
        @Param("itemId") itemId: String,
        @Param("ok") ok: Boolean,
        @Param("observacao") observacao: String?,
        @Param("registradoEm") registradoEm: java.time.LocalDateTime,
        @Param("recebidoEm") recebidoEm: java.time.LocalDateTime
    )

    @Query(value = """
        -- Colunas explícitas (lidas por posição no ChecklistController). Não usar cr.*.
        SELECT cr.id, cr.diario_id, cr.item_id, cr.observacao, cr.ok, cr.registrado_em,
               ci.descricao, ci.obrigatorio, ci.ordem
        FROM checklist_respostas cr
        INNER JOIN checklist_itens ci ON ci.id = cr.item_id
        WHERE cr.diario_id = CAST(:diarioId AS uuid)
        ORDER BY ci.ordem
    """, nativeQuery = true)
    fun findByDiarioId(
        @org.springframework.data.repository.query.Param("diarioId") diarioId: String
    ): List<Array<Any>>
}