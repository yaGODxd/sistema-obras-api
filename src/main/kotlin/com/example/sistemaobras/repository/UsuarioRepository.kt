package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Repository
interface UsuarioRepository : JpaRepository<Usuario, UUID> {

    fun findByLogin(login: String): Usuario?

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO usuarios (nome_completo, cpf, login, senha_hash, perfil, ativo,
                matricula, vinculo, secretaria_id, categoria_cnh, validade_cnh, telefone)
            VALUES (:nomeCompleto, :cpf, :login, :senhaHash, CAST(:perfil AS perfil_usuario), :ativo,
                :matricula, :vinculo, :secretariaId, :categoriaCnh, :validadeCnh, :telefone)
        """,
        nativeQuery = true
    )
    fun inserirUsuario(
        @org.springframework.data.repository.query.Param("nomeCompleto") nomeCompleto: String,
        @org.springframework.data.repository.query.Param("cpf") cpf: String,
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("senhaHash") senhaHash: String,
        @org.springframework.data.repository.query.Param("perfil") perfil: String,
        @org.springframework.data.repository.query.Param("ativo") ativo: Boolean,
        @org.springframework.data.repository.query.Param("matricula") matricula: String? = null,
        @org.springframework.data.repository.query.Param("vinculo") vinculo: String? = null,
        @org.springframework.data.repository.query.Param("secretariaId") secretariaId: Int? = null,
        @org.springframework.data.repository.query.Param("categoriaCnh") categoriaCnh: String? = null,
        @org.springframework.data.repository.query.Param("validadeCnh") validadeCnh: LocalDate? = null,
        @org.springframework.data.repository.query.Param("telefone") telefone: String? = null
    )

    @Modifying
    @Transactional
    @Query(
        value = "UPDATE usuarios SET foto_perfil = :fotoPerfil, atualizado_em = NOW() WHERE login = :login",
        nativeQuery = true
    )
    fun atualizarFoto(
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("fotoPerfil") fotoPerfil: String
    )

    @Modifying
    @Transactional
    @Query(
        value = "UPDATE usuarios SET nome_completo = :nomeCompleto, atualizado_em = NOW() WHERE login = :login",
        nativeQuery = true
    )
    fun atualizarNome(
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("nomeCompleto") nomeCompleto: String
    )

    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE usuarios SET
                nome_completo = COALESCE(:nomeCompleto, nome_completo),
                foto_perfil = COALESCE(:fotoPerfil, foto_perfil),
                matricula = :matricula,
                vinculo = :vinculo,
                secretaria_id = :secretariaId,
                categoria_cnh = :categoriaCnh,
                validade_cnh = :validadeCnh,
                telefone = :telefone,
                atualizado_em = NOW()
            WHERE login = :login
        """,
        nativeQuery = true
    )
    fun atualizarUsuarioCompleto(
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("nomeCompleto") nomeCompleto: String?,
        @org.springframework.data.repository.query.Param("fotoPerfil") fotoPerfil: String?,
        @org.springframework.data.repository.query.Param("matricula") matricula: String?,
        @org.springframework.data.repository.query.Param("vinculo") vinculo: String?,
        @org.springframework.data.repository.query.Param("secretariaId") secretariaId: Int?,
        @org.springframework.data.repository.query.Param("categoriaCnh") categoriaCnh: String?,
        @org.springframework.data.repository.query.Param("validadeCnh") validadeCnh: LocalDate?,
        @org.springframework.data.repository.query.Param("telefone") telefone: String?
    )

    @Query(
        value = """
        SELECT u.id, u.nome_completo, u.login, u.foto_perfil, t.aberto_em
        FROM usuarios u
        INNER JOIN turnos t ON t.usuario_id = u.id
        WHERE t.status = 'aberto'
        AND u.perfil = 'motorista'
        ORDER BY t.aberto_em DESC
    """,
        nativeQuery = true
    )
    fun findMotoristasOnline(): List<Array<Any>>

    @Modifying
    @Transactional
    @Query(
        value = "UPDATE usuarios SET ativo = :ativo, atualizado_em = NOW() WHERE login = :login",
        nativeQuery = true
    )
    fun alterarAtivo(
        @org.springframework.data.repository.query.Param("login") login: String,
        @org.springframework.data.repository.query.Param("ativo") ativo: Boolean
    )

    @Query(
        value = "SELECT foto_perfil FROM usuarios WHERE login = :login",
        nativeQuery = true
    )
    fun findFotoByLogin(
        @org.springframework.data.repository.query.Param("login") login: String
    ): String?
}