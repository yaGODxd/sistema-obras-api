package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface UsuarioRepository : JpaRepository<Usuario, UUID> {

    fun findByLogin(login: String): Usuario?

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO usuarios (nome_completo, cpf, login, senha_hash, perfil, ativo)
            VALUES (:nomeCompleto, :cpf, :login, :senhaHash, CAST(:perfil AS perfil_usuario), :ativo)
        """,
        nativeQuery = true
    )
    fun inserirUsuario(
        nomeCompleto: String,
        cpf: String,
        login: String,
        senhaHash: String,
        perfil: String,
        ativo: Boolean
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
}
