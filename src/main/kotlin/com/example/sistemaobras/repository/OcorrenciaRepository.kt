package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Ocorrencia
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OcorrenciaRepository : JpaRepository<Ocorrencia, UUID> {

    fun findByDiarioId(diarioId: UUID): List<Ocorrencia>

    @Query("""
        SELECT o.* FROM ocorrencias o
        INNER JOIN diarios_bordo d ON o.diario_id = d.id
        WHERE d.veiculo_id = CAST(:veiculoId AS uuid)
        ORDER BY o.registrado_em DESC
    """, nativeQuery = true)
    fun findByVeiculoId(@Param("veiculoId") veiculoId: UUID): List<Ocorrencia>

    @Query("""
        SELECT o.* FROM ocorrencias o
        INNER JOIN diarios_bordo d ON o.diario_id = d.id
        INNER JOIN usuarios u ON u.id = d.usuario_id
        WHERE u.login = :login
        ORDER BY o.registrado_em DESC
    """, nativeQuery = true)
    fun findByMotoristaLogin(@Param("login") login: String): List<Ocorrencia>
}