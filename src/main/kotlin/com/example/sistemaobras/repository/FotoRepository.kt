package com.example.sistemaobras.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDateTime
import java.util.UUID

/**
 * Parte 9: fotos guardadas no banco (coluna bytea "conteudo").
 * SQL direto (sem entidade JPA) para que NENHUMA consulta de metadados traga a imagem:
 * só lerConteudo() seleciona a coluna conteudo.
 */
@Repository
class FotoRepository(private val jdbc: NamedParameterJdbcTemplate) {

    data class FotoMetadados(
        val id: UUID,
        val diarioId: UUID,
        val tipo: String,
        val ocorrenciaId: UUID?,
        val abastecimentoId: UUID?,
        val tiradaEm: LocalDateTime?,
        val recebidaEm: LocalDateTime?,
        val tamanhoBytes: Int?,
        val tipoMime: String?,
        val hashSha256: String?,
        val idLocal: UUID?,
        val horarioSuspeito: Boolean,
        val motivoSuspeita: String?,
        val registradoPor: String?
    )

    data class NovaFoto(
        val id: UUID,
        val diarioId: UUID,
        val tipo: String,
        val ocorrenciaId: UUID?,
        val abastecimentoId: UUID?,
        val conteudo: ByteArray?,
        val caminhoArquivo: String?,
        val tipoMime: String,
        val tamanhoBytes: Int,
        val hashSha256: String,
        val idLocal: UUID?,
        val tiradaEm: LocalDateTime,
        val recebidaEm: LocalDateTime,
        val registradoPor: String?,
        val desvioSeg: Int?,
        val suspeito: Boolean,
        val motivo: String?
    )

    // Abastecimento ao qual a foto de cupom/bomba se liga.
    data class AbastecimentoDaFoto(val diarioId: UUID, val registradoPor: String?)

    fun inserir(f: NovaFoto) {
        val p = MapSqlParameterSource()
            .addValue("id", f.id, Types.OTHER)
            .addValue("diarioId", f.diarioId, Types.OTHER)
            .addValue("ocorrenciaId", f.ocorrenciaId, Types.OTHER)
            .addValue("abastecimentoId", f.abastecimentoId, Types.OTHER)
            .addValue("tipo", f.tipo, Types.VARCHAR)
            .addValue("caminho", f.caminhoArquivo, Types.VARCHAR)
            .addValue("conteudo", f.conteudo, Types.BINARY)
            .addValue("mime", f.tipoMime, Types.VARCHAR)
            .addValue("tamanho", f.tamanhoBytes, Types.INTEGER)
            .addValue("hash", f.hashSha256, Types.VARCHAR)
            .addValue("idLocal", f.idLocal, Types.OTHER)
            .addValue("tiradaEm", f.tiradaEm, Types.TIMESTAMP)
            .addValue("recebidaEm", f.recebidaEm, Types.TIMESTAMP)
            .addValue("registradoPor", f.registradoPor, Types.VARCHAR)
            .addValue("desvio", f.desvioSeg, Types.INTEGER)
            .addValue("suspeito", f.suspeito, Types.BOOLEAN)
            .addValue("motivo", f.motivo, Types.VARCHAR)
        jdbc.update(
            """
            INSERT INTO fotos (id, diario_id, ocorrencia_id, abastecimento_id, tipo, caminho_arquivo, conteudo,
                               tipo_mime, tamanho_bytes, hash_sha256, id_local, tirada_em, recebida_em,
                               registrado_por, desvio_relogio_seg, horario_suspeito, motivo_suspeita, sincronizado)
            VALUES (:id, :diarioId, :ocorrenciaId, :abastecimentoId, CAST(:tipo AS tipo_foto), :caminho, :conteudo,
                    :mime, :tamanho, :hash, :idLocal, :tiradaEm, :recebidaEm,
                    (SELECT u.id FROM usuarios u WHERE u.login = :registradoPor),
                    :desvio, :suspeito, :motivo, false)
            """.trimIndent(),
            p
        )
    }

    fun buscarMetadados(id: UUID): FotoMetadados? =
        jdbc.query("$METADADOS WHERE f.id = :id", MapSqlParameterSource("id", id), ::lerMetadados).firstOrNull()

    fun buscarPorIdLocal(idLocal: UUID): FotoMetadados? =
        jdbc.query("$METADADOS WHERE f.id_local = :idLocal", MapSqlParameterSource("idLocal", idLocal), ::lerMetadados)
            .firstOrNull()

    // Etapa 3: metadados das fotos de um diário (sem os bytes), na ordem em que foram tiradas.
    fun listarDoDiario(diarioId: UUID): List<FotoMetadados> =
        jdbc.query("$METADADOS WHERE f.diario_id = :diarioId ORDER BY f.tirada_em, f.id",
            MapSqlParameterSource("diarioId", diarioId), ::lerMetadados)

    data class ConteudoGuardado(val conteudo: ByteArray?, val caminhoArquivo: String?)

    // Única consulta que lê a coluna conteudo (uma foto por vez).
    fun lerConteudo(id: UUID): ConteudoGuardado? =
        jdbc.query("SELECT conteudo, caminho_arquivo FROM fotos WHERE id = :id", MapSqlParameterSource("id", id)) { rs, _ ->
            ConteudoGuardado(rs.getBytes("conteudo"), rs.getString("caminho_arquivo"))
        }.firstOrNull()

    data class Uso(val quantidade: Long, val bytesFotos: Long, val bytesTabela: Long, val bytesBanco: Long)

    // Espaço usado (GET /fotos/uso).
    fun uso(): Uso =
        jdbc.query(
            """
            SELECT COUNT(*) AS quantidade, COALESCE(SUM(tamanho_bytes), 0) AS bytes_fotos,
                   pg_total_relation_size('fotos') AS bytes_tabela, pg_database_size(current_database()) AS bytes_banco
            FROM fotos
            """.trimIndent(),
            MapSqlParameterSource()
        ) { rs, _ -> Uso(rs.getLong("quantidade"), rs.getLong("bytes_fotos"), rs.getLong("bytes_tabela"), rs.getLong("bytes_banco")) }
            .first()

    fun diarioDaOcorrencia(ocorrenciaId: UUID): UUID? =
        jdbc.query(
            "SELECT diario_id FROM ocorrencias WHERE id = :id", MapSqlParameterSource("id", ocorrenciaId)
        ) { rs, _ -> rs.getObject("diario_id", UUID::class.java) }.firstOrNull()

    fun abastecimentoDaFoto(abastecimentoId: UUID): AbastecimentoDaFoto? =
        jdbc.query(
            """
            SELECT a.diario_id, rp.login AS registrado_por
            FROM abastecimentos a LEFT JOIN usuarios rp ON rp.id = a.registrado_por
            WHERE a.id = :id
            """.trimIndent(),
            MapSqlParameterSource("id", abastecimentoId)
        ) { rs, _ -> AbastecimentoDaFoto(rs.getObject("diario_id", UUID::class.java), rs.getString("registrado_por")) }
            .firstOrNull()

    private fun lerMetadados(rs: ResultSet, @Suppress("UNUSED_PARAMETER") linha: Int) = FotoMetadados(
        id = rs.getObject("id", UUID::class.java),
        diarioId = rs.getObject("diario_id", UUID::class.java),
        tipo = rs.getString("tipo"),
        ocorrenciaId = rs.getObject("ocorrencia_id", UUID::class.java),
        abastecimentoId = rs.getObject("abastecimento_id", UUID::class.java),
        tiradaEm = rs.getObject("tirada_em", LocalDateTime::class.java),
        recebidaEm = rs.getObject("recebida_em", LocalDateTime::class.java),
        tamanhoBytes = rs.getObject("tamanho_bytes") as Int?,
        tipoMime = rs.getString("tipo_mime"),
        hashSha256 = rs.getString("hash_sha256"),
        idLocal = rs.getObject("id_local", UUID::class.java),
        horarioSuspeito = rs.getBoolean("horario_suspeito"),
        motivoSuspeita = rs.getString("motivo_suspeita"),
        registradoPor = rs.getString("registrado_por_login")
    )

    companion object {
        // Metadados SEM a coluna conteudo (nunca devolver bytes em lista).
        const val METADADOS = """
            SELECT f.id, f.diario_id, CAST(f.tipo AS text) AS tipo, f.ocorrencia_id, f.abastecimento_id,
                   f.tirada_em, f.recebida_em, f.tamanho_bytes, f.tipo_mime, f.hash_sha256, f.id_local,
                   f.horario_suspeito, f.motivo_suspeita, rp.login AS registrado_por_login
            FROM fotos f
            LEFT JOIN usuarios rp ON rp.id = f.registrado_por
        """
    }
}
