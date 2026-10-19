package com.example.sistemaobras.service

import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.NaoAutorizadoException
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.dto.*
import com.example.sistemaobras.entity.Usuario
import java.time.LocalDate
import java.util.UUID
import com.example.sistemaobras.repository.SecretariaRepository
import com.example.sistemaobras.repository.UsuarioRepository
import com.example.sistemaobras.security.JwtService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class UsuarioService(
    private val usuarioRepository: UsuarioRepository,
    private val jwtService: JwtService,
    private val logService: LogService,
    private val secretariaRepository: SecretariaRepository,
    // Desligado na fase de testes; ligar com APP_VALIDAR_CPF_DIGITOS=true.
    @Value("\${app.validar-cpf-digitos:false}") private val validarCpfDigitos: Boolean = false
) {
    private val bcrypt = BCryptPasswordEncoder()

    private fun toResponse(u: Usuario): UsuarioResponse {
        val secretariaNome = u.secretariaId?.let {
            try { secretariaRepository.findById(it).orElse(null)?.nome } catch (e: Exception) { null }
        }
        return UsuarioResponse(
            id = u.id,
            nomeCompleto = u.nomeCompleto,
            login = u.login,
            perfil = u.perfil.name,
            fotoPerfil = u.fotoPerfil,
            ativo = u.ativo,
            matricula = u.matricula,
            vinculo = u.vinculo,
            secretariaId = u.secretariaId,
            secretariaNome = secretariaNome,
            categoriaCnh = u.categoriaCnh,
            validadeCnh = u.validadeCnh,
            telefone = u.telefone
        )
    }

    fun login(request: LoginRequest): LoginResponse {
        // Mesma mensagem para login inexistente e senha errada, e a senha é
        // conferida antes de revelar que o usuário está inativo.
        val usuario = usuarioRepository.findByLogin(request.login)
        if (usuario == null || !bcrypt.matches(request.senha, usuario.senhaHash))
            throw NaoAutorizadoException("Login ou senha incorretos.")
        if (!usuario.ativo) throw NaoAutorizadoException("Usuário inativo. Procure o gestor.")

        val token = jwtService.gerarToken(usuario.login, usuario.perfil.name)

        logService.registrar(
            usuarioLogin = usuario.login,
            usuarioNome = usuario.nomeCompleto,
            acao = "LOGIN",
            descricao = "Usuário realizou login no sistema"
        )

        return LoginResponse(token = token, nome = usuario.nomeCompleto, perfil = usuario.perfil.name)
    }

    fun criar(request: CriarUsuarioRequest): LoginResponse {
        if (usuarioRepository.findByLogin(request.login) != null)
            throw ConflitoException("Já existe um usuário com este login.")

        if (validarCpfDigitos && !ValidadorCpf.valido(request.cpf))
            throw RegraNegocioException("CPF inválido.")
        val cpfDigitos = ValidadorCpf.somenteDigitos(request.cpf)
        if (cpfDigitos.isNotEmpty() && usuarioRepository.contarPorCpf(cpfDigitos) > 0)
            throw ConflitoException("Já existe um usuário com este CPF.")

        usuarioRepository.inserirUsuario(
            nomeCompleto = request.nomeCompleto,
            cpf = request.cpf,
            login = request.login,
            senhaHash = bcrypt.encode(request.senha)!!,
            perfil = request.perfil,
            ativo = true,
            matricula = request.matricula,
            vinculo = request.vinculo,
            secretariaId = request.secretariaId,
            categoriaCnh = request.categoriaCnh,
            validadeCnh = request.validadeCnh?.let { LocalDate.parse(it) },
            telefone = request.telefone
        )

        val token = jwtService.gerarToken(request.login, request.perfil)

        logService.registrar(
            usuarioLogin = request.login,
            usuarioNome = request.nomeCompleto,
            acao = "CADASTRO",
            descricao = "Novo usuário cadastrado com perfil ${request.perfil}"
        )

        return LoginResponse(token = token, nome = request.nomeCompleto, perfil = request.perfil)
    }

    fun atualizarPerfil(login: String, request: AtualizarPerfilRequest): UsuarioResponse {
        usuarioRepository.findByLogin(login)
            ?: throw NaoEncontradoException("Usuário não encontrado.")

        if (request.fotoPerfil != null) usuarioRepository.atualizarFoto(login, request.fotoPerfil)
        if (request.nomeCompleto != null) {
            usuarioRepository.atualizarNome(login, request.nomeCompleto)
            logService.registrar(
                usuarioLogin = login,
                usuarioNome = request.nomeCompleto,
                acao = "ATUALIZAR_PERFIL",
                descricao = "Usuário atualizou seu perfil"
            )
        }

        return toResponse(usuarioRepository.findByLogin(login)!!)
    }

    fun atualizarUsuario(login: String, request: AtualizarUsuarioRequest): UsuarioResponse {
        usuarioRepository.findByLogin(login)
            ?: throw NaoEncontradoException("Usuário não encontrado.")

        usuarioRepository.atualizarUsuarioCompleto(
            login = login,
            nomeCompleto = request.nomeCompleto,
            fotoPerfil = request.fotoPerfil,
            matricula = request.matricula,
            vinculo = request.vinculo,
            secretariaId = request.secretariaId,
            categoriaCnh = request.categoriaCnh,
            validadeCnh = request.validadeCnh?.let { LocalDate.parse(it) },
            telefone = request.telefone
        )

        logService.registrar(
            usuarioLogin = login,
            usuarioNome = request.nomeCompleto ?: login,
            acao = "ATUALIZAR_PERFIL",
            descricao = "Dados do usuário atualizados"
        )

        return toResponse(usuarioRepository.findByLogin(login)!!)
    }

    fun buscarPerfil(login: String): UsuarioResponse {
        val usuario = usuarioRepository.findByLogin(login)
            ?: throw NaoEncontradoException("Usuário não encontrado.")
        return toResponse(usuario)
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

    fun listarTodos(): List<UsuarioResponse> {
        return usuarioRepository.findAll().map { toResponse(it) }
    }

    fun alterarAtivo(login: String, ativo: Boolean) {
        usuarioRepository.findByLogin(login)
            ?: throw NaoEncontradoException("Usuário não encontrado.")
        usuarioRepository.alterarAtivo(login, ativo)

        logService.registrar(
            usuarioLogin = "sistema",
            usuarioNome = "Sistema",
            acao = if (ativo) "USUARIO_ATIVADO" else "USUARIO_DESATIVADO",
            descricao = "Usuário '$login' foi ${if (ativo) "ativado" else "desativado"}"
        )
    }
}