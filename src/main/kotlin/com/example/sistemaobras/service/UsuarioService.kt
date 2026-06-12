package com.example.sistemaobras.service

import com.example.sistemaobras.dto.AtualizarPerfilRequest
import com.example.sistemaobras.dto.CriarUsuarioRequest
import com.example.sistemaobras.dto.LoginRequest
import com.example.sistemaobras.dto.LoginResponse
import com.example.sistemaobras.dto.UsuarioResponse
import com.example.sistemaobras.dto.MotoristaOnlineResponse
import java.util.UUID
import com.example.sistemaobras.repository.UsuarioRepository
import com.example.sistemaobras.security.JwtService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class UsuarioService(
    private val usuarioRepository: UsuarioRepository,
    private val jwtService: JwtService
) {
    private val bcrypt = BCryptPasswordEncoder()

    fun login(request: LoginRequest): LoginResponse {
        val usuario = usuarioRepository.findByLogin(request.login)
            ?: throw RuntimeException("Usuário não encontrado")

        if (!usuario.ativo)
            throw RuntimeException("Usuário inativo")

        if (!bcrypt.matches(request.senha, usuario.senhaHash))
            throw RuntimeException("Senha incorreta")

        val token = jwtService.gerarToken(usuario.login, usuario.perfil.name)

        return LoginResponse(
            token = token,
            nome = usuario.nomeCompleto,
            perfil = usuario.perfil.name
        )
    }

    fun criar(request: CriarUsuarioRequest): LoginResponse {
        if (usuarioRepository.findByLogin(request.login) != null)
            throw RuntimeException("Login já existe")

        usuarioRepository.inserirUsuario(
            nomeCompleto = request.nomeCompleto,
            cpf = request.cpf,
            login = request.login,
            senhaHash = bcrypt.encode(request.senha)!!,
            perfil = request.perfil,
            ativo = true
        )

        val token = jwtService.gerarToken(request.login, request.perfil)

        return LoginResponse(
            token = token,
            nome = request.nomeCompleto,
            perfil = request.perfil
        )
    }

    fun atualizarPerfil(login: String, request: AtualizarPerfilRequest): UsuarioResponse {
        val usuario = usuarioRepository.findByLogin(login)
            ?: throw RuntimeException("Usuário não encontrado")

        if (request.fotoPerfil != null) {
            usuarioRepository.atualizarFoto(login, request.fotoPerfil)
        }

        if (request.nomeCompleto != null) {
            usuarioRepository.atualizarNome(login, request.nomeCompleto)
        }

        val atualizado = usuarioRepository.findByLogin(login)!!

        return UsuarioResponse(
            id = atualizado.id,
            nomeCompleto = atualizado.nomeCompleto,
            login = atualizado.login,
            perfil = atualizado.perfil.name,
            fotoPerfil = atualizado.fotoPerfil
        )


    }

    fun buscarPerfil(login: String): UsuarioResponse {
        val usuario = usuarioRepository.findByLogin(login)
            ?: throw RuntimeException("Usuário não encontrado")

        return UsuarioResponse(
            id = usuario.id,
            nomeCompleto = usuario.nomeCompleto,
            login = usuario.login,
            perfil = usuario.perfil.name,
            fotoPerfil = usuario.fotoPerfil
        )
    }

    fun listarMotoristasOnline(): List<MotoristaOnlineResponse> {
        return usuarioRepository.findMotoristasOnline().map { row ->
            MotoristaOnlineResponse(
                id = UUID.fromString(row[0].toString()),
                nomeCompleto = row[1].toString(),
                login = row[2].toString(),
                fotoPerfil = row[3]?.toString(),
                turnoAbertoEm = if (row[4] != null)
                    java.time.LocalDateTime.parse(row[4].toString().replace(" ", "T"))
                else null
            )
        }
    }
}