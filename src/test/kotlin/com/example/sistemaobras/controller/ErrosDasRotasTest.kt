package com.example.sistemaobras.controller

import com.example.sistemaobras.dto.TurnoRequest
import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.NaoAutorizadoException
import com.example.sistemaobras.exception.NaoEncontradoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.repository.*
import com.example.sistemaobras.security.JwtService
import com.example.sistemaobras.security.SecurityConfig
import com.example.sistemaobras.service.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.SQLException

// Uma rota de cada controller alterado na Parte 1: status antigo mantido, corpo {"erro"}.
// Usa a SecurityConfig real (para testar o 401) e mocks no lugar de services e repositórios.
@WebMvcTest
@Import(SecurityConfig::class)
class ErrosDasRotasTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var jwtService: JwtService
    @MockitoBean lateinit var usuarioService: UsuarioService
    @MockitoBean lateinit var veiculoService: VeiculoService
    @MockitoBean lateinit var manutencaoService: ManutencaoService
    @MockitoBean lateinit var abastecimentoService: AbastecimentoService
    @MockitoBean lateinit var ocorrenciaService: OcorrenciaService
    @MockitoBean lateinit var rastreamentoService: RastreamentoService
    @MockitoBean lateinit var turnoService: TurnoService
    @MockitoBean lateinit var diarioBordoService: DiarioBordoService
    @MockitoBean lateinit var assinaturaService: AssinaturaService
    @MockitoBean lateinit var logService: LogService
    @MockitoBean lateinit var diarioDoEvento: DiarioDoEvento
    @MockitoBean lateinit var usuarioRepository: UsuarioRepository
    @MockitoBean lateinit var mecanicoRepository: MecanicoRepository
    @MockitoBean lateinit var postoRepository: PostoRepository
    @MockitoBean lateinit var secretariaRepository: SecretariaRepository
    @MockitoBean lateinit var motivoUsoRepository: MotivoUsoRepository
    @MockitoBean lateinit var fotoService: FotoService
    @MockitoBean lateinit var checklistTemplateRepository: ChecklistTemplateRepository
    @MockitoBean lateinit var checklistItemRepository: ChecklistItemRepository
    @MockitoBean lateinit var checklistRespostaRepository: ChecklistRespostaRepository

    // Mockito.any() devolve null; o cast evita a checagem de nulo do Kotlin na chamada.
    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        Mockito.any<T>()
        return null as T
    }

    private fun json(req: MockHttpServletRequestBuilder, corpo: String) =
        mockMvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(corpo))

    private val uuid = "11111111-1111-1111-1111-111111111111"

    @Test
    fun `login errado responde 401 com mensagem`() {
        given(usuarioService.login(qualquer())).willThrow(NaoAutorizadoException("Login ou senha incorretos."))
        json(post("/auth/login"), """{"login":"x","senha":"y"}""")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.erro").value("Login ou senha incorretos."))
    }

    @Test
    fun `cadastro com login repetido mantem 400`() {
        given(usuarioService.criar(qualquer())).willThrow(ConflitoException("Já existe um usuário com este login."))
        json(post("/auth/cadastrar"), """{"nomeCompleto":"A","cpf":"1","login":"a","senha":"b"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Já existe um usuário com este login."))
    }

    @Test
    fun `rota protegida sem token responde 401 com corpo`() {
        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.erro").value("Não autenticado. Faça login novamente."))
    }

    @Test
    fun `usuario inexistente mantem 404 no GET e 400 no PUT`() {
        given(usuarioService.buscarPerfil("joao")).willThrow(NaoEncontradoException("Usuário não encontrado."))
        mockMvc.perform(get("/usuarios/joao"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.erro").value("Usuário não encontrado."))

        given(usuarioService.atualizarUsuario(anyString(), qualquer())).willThrow(NaoEncontradoException("Usuário não encontrado."))
        json(put("/usuarios/joao"), "{}")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Usuário não encontrado."))
    }

    @Test
    fun `veiculo inexistente no PUT mantem 400`() {
        willThrow(NaoEncontradoException("Veículo não encontrado.")).given(veiculoService).atualizar(anyString(), qualquer())
        json(put("/veiculos/$uuid"), """{"tipoId":"$uuid","descricao":"Caminhão"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Veículo não encontrado."))
    }

    @Test
    fun `erro inesperado em rota sem status fixo responde 500`() {
        given(veiculoService.listarTodos()).willThrow(IllegalStateException("falha"))
        mockMvc.perform(get("/veiculos"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.erro").value("Erro interno. Tente novamente."))
    }

    @Test
    fun `mecanico inexistente no PUT mantem 400`() {
        json(put("/mecanicos/$uuid"), """{"nome":"Ze"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Mecânico não encontrado."))
    }

    @Test
    fun `posto com id invalido no PUT mantem 400`() {
        json(put("/postos/abc"), """{"nome":"Posto"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Identificador inválido."))
    }

    @Test
    fun `cadastro de mecanico sem nome responde 400 pelo Spring`() {
        json(post("/mecanicos"), "{}")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo ausente ou com valor inválido: nome."))
    }

    @Test
    fun `manutencao mantem 404 no GET e 400 no POST`() {
        given(manutencaoService.buscarPorId(uuid)).willThrow(NaoEncontradoException("Manutenção não encontrada."))
        mockMvc.perform(get("/manutencoes/$uuid"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.erro").value("Manutenção não encontrada."))

        given(manutencaoService.abrir(qualquer())).willThrow(IllegalStateException("falha no banco"))
        json(post("/manutencoes"), """{"veiculoId":"$uuid","tipo":"corretiva","descricao":"Freio"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Erro interno. Tente novamente."))
    }

    @Test
    fun `abastecimento sem diario aberto mantem 400 com mensagem`() {
        given(abastecimentoService.registrar(qualquer())).willThrow(RegraNegocioException("Motorista não tem diário aberto"))
        json(post("/abastecimentos"), """{"loginMotorista":"m","tipoCombustivel":"diesel","litros":10,"valorTotal":60}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Motorista não tem diário aberto"))
    }

    @Test
    fun `ocorrencia sem diario aberto mantem 400 com mensagem`() {
        given(ocorrenciaService.registrar(qualquer())).willThrow(RegraNegocioException("Motorista não tem diário aberto"))
        json(post("/ocorrencias"), """{"loginMotorista":"m","tipo":"pneu","descricao":"furado"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Motorista não tem diário aberto"))
    }

    @Test
    fun `rastreamento com erro do banco mantem 400`() {
        willThrow(DataIntegrityViolationException("x", SQLException("violates foreign key", "23503")))
            .given(rastreamentoService).registrarPonto(qualquer())
        json(post("/rastreamento"), """{"loginMotorista":"m","latitude":1,"longitude":2}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Registro relacionado não encontrado ou ainda em uso."))
    }

    @Test
    fun `turno ja aberto mantem 400 com mensagem`() {
        given(turnoService.abrirTurno(TurnoRequest("m"))).willThrow(RegraNegocioException("Já existe um turno aberto para este usuário."))
        json(post("/turnos/abrir"), """{"login":"m"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Já existe um turno aberto para este usuário."))
    }

    @Test
    fun `abrir diario sem turno mantem 400 e a mesma mensagem de antes`() {
        given(diarioBordoService.abrirDiario(qualquer())).willThrow(RegraNegocioException("Motorista não tem turno aberto"))
        json(post("/diarios/abrir"), """{"loginMotorista":"m","veiculoId":"$uuid","medidorInicial":100}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Motorista não tem turno aberto"))
    }

    // ---------- Parte 2: validações ----------

    @Test
    fun `valor negativo e recusado com 400`() {
        json(post("/abastecimentos"), """{"loginMotorista":"m","tipoCombustivel":"diesel","litros":-1,"valorTotal":60}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'litros' deve ser maior que zero."))
        json(post("/diarios/fechar/m"), """{"medidorFinal":-5}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'medidorFinal' não pode ser negativo."))
        json(post("/veiculos"), """{"tipoId":"$uuid","descricao":"Caminhão","medidorAtual":-1}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'medidorAtual' não pode ser negativo."))
    }

    @Test
    fun `peca com quantidade negativa dentro da manutencao e recusada`() {
        json(post("/manutencoes"), """{"veiculoId":"$uuid","tipo":"corretiva","descricao":"Freio",
            "pecas":[{"nomePeca":"Pastilha","quantidade":-2,"valorUnitario":10}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'pecas[0].quantidade' deve ser no mínimo 1."))
        json(post("/manutencoes/$uuid/pecas"), """{"nomePeca":"Pastilha","quantidade":1,"valorUnitario":-10}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'valorUnitario' não pode ser negativo."))
    }

    @Test
    fun `texto obrigatorio em branco e recusado`() {
        json(post("/postos"), """{"nome":"  "}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'nome' é obrigatório."))
        json(post("/auth/cadastrar"), """{"nomeCompleto":"A","cpf":"","login":"a","senha":"b"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'cpf' é obrigatório."))
    }

    @Test
    fun `perfil desconhecido e recusado antes de chegar no banco`() {
        json(post("/auth/cadastrar"), """{"nomeCompleto":"A","cpf":"1","login":"a","senha":"b","perfil":"admin"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'perfil' deve ser gestor ou motorista."))
    }

    @Test
    fun `texto maior que a coluna do banco e recusado`() {
        val longo = "x".repeat(256)
        json(post("/ocorrencias"), """{"loginMotorista":"m","tipo":"pneu","descricao":"$longo"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'descricao' deve ter no máximo 255 caracteres."))
        json(put("/manutencoes/$uuid/status"), """{"status":"concluida","observacoes":"$longo"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'observacoes' deve ter no máximo 255 caracteres."))
        json(post("/checklist/respostas"), """{"diarioId":"$uuid","respostas":[{"itemId":"$uuid","ok":true,"observacao":"$longo"}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'respostas[0].observacao' deve ter no máximo 255 caracteres."))
    }

    @Test
    fun `foto de perfil grande nao esbarra em limite do JSON`() {
        val foto = "a".repeat(5000)
        json(put("/usuarios/joao/perfil"), """{"fotoPerfil":"$foto"}""")
            .andExpect(status().isOk)
    }

    @Test
    fun `foto de perfil invalida responde 400 com codigo nas duas rotas`() {
        val erro = RegraNegocioException("A foto de perfil precisa ser uma imagem JPEG ou PNG.", CodigosErro.FOTO_PERFIL_INVALIDA)
        given(usuarioService.atualizarPerfil(anyString(), qualquer())).willThrow(erro)
        given(usuarioService.atualizarUsuario(anyString(), qualquer())).willThrow(erro)
        json(put("/usuarios/joao/perfil"), """{"fotoPerfil":"lixo"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("FOTO_PERFIL_INVALIDA"))
        json(put("/usuarios/joao"), """{"fotoPerfil":"lixo"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("A foto de perfil precisa ser uma imagem JPEG ou PNG."))
            .andExpect(jsonPath("$.codigo").value("FOTO_PERFIL_INVALIDA"))
    }

    @Test
    fun `placa repetida mantem 400 com mensagem`() {
        willThrow(ConflitoException("Já existe um veículo com esta placa.")).given(veiculoService).cadastrar(qualquer())
        json(post("/veiculos"), """{"tipoId":"$uuid","placa":"ABC1D23","descricao":"Caminhão"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Já existe um veículo com esta placa."))
    }

    // ---------- Parte 3 ----------

    @Test
    fun `zero litros e quantidade zero sao recusados mas valor zero e aceito`() {
        json(post("/abastecimentos"), """{"loginMotorista":"m","tipoCombustivel":"diesel","litros":0,"valorTotal":60}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'litros' deve ser maior que zero."))
        json(post("/manutencoes/$uuid/pecas"), """{"nomePeca":"Pastilha","quantidade":0,"valorUnitario":0}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'quantidade' deve ser no mínimo 1."))
        json(post("/abastecimentos"), """{"loginMotorista":"m","tipoCombustivel":"diesel","litros":10,"valorTotal":0}""")
            .andExpect(status().isOk)
        json(post("/manutencoes/$uuid/pecas"), """{"nomePeca":"Pastilha","quantidade":1,"valorUnitario":0}""")
            .andExpect(status().isOk)
    }

    @Test
    fun `status de manutencao desconhecido e recusado`() {
        json(put("/manutencoes/$uuid/status"), """{"status":"finalizada"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'status' deve ser aberta, em_andamento ou concluida."))
    }

    @Test
    fun `medidor final menor que o inicial mantem 400 com mensagem`() {
        given(diarioBordoService.fecharDiario(anyString(), qualquer()))
            .willThrow(RegraNegocioException("O medidor final não pode ser menor que o medidor inicial."))
        json(post("/diarios/fechar/m"), """{"medidorFinal":10}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("O medidor final não pode ser menor que o medidor inicial."))
    }

    @Test
    fun `status da ocorrencia a mao`() {
        // "concluida" é da manutenção; na ocorrência é "resolvida".
        json(put("/ocorrencias/$uuid/status"), """{"status":"concluida"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Campo 'status' deve ser pendente, em_andamento ou resolvida."))

        given(ocorrenciaService.atualizarStatus(uuid, "resolvida")).willThrow(NaoEncontradoException("Ocorrência não encontrada."))
        json(put("/ocorrencias/$uuid/status"), """{"status":"resolvida"}""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.erro").value("Ocorrência não encontrada."))

        json(put("/ocorrencias/$uuid/status"), """{"status":"pendente"}""")
            .andExpect(status().isOk)
    }

    @Test
    fun `lista de ocorrencias aceita filtro de status`() {
        mockMvc.perform(get("/ocorrencias").param("status", "pendente"))
            .andExpect(status().isOk)
        Mockito.verify(ocorrenciaService).listarTodas("pendente")

        given(ocorrenciaService.listarTodas("aberta")).willThrow(
            RegraNegocioException("Status inválido. Use pendente, em_andamento ou resolvida.", CodigosErro.STATUS_INVALIDO))
        mockMvc.perform(get("/ocorrencias").param("status", "aberta"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("STATUS_INVALIDO"))
    }

    // ---------- Parte 4 ----------

    @Test
    fun `detalhe do diario responde 400 e 404 nos erros`() {
        given(diarioBordoService.buscarDetalhe("abc")).willThrow(RegraNegocioException("Identificador inválido."))
        mockMvc.perform(get("/diarios/abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Identificador inválido."))
        given(diarioBordoService.buscarDetalhe(uuid)).willThrow(NaoEncontradoException("Diário não encontrado."))
        mockMvc.perform(get("/diarios/$uuid"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.erro").value("Diário não encontrado."))
    }

    @Test
    fun `rotas fixas de diario nao sao confundidas com o detalhe`() {
        mockMvc.perform(get("/diarios/resumo-mensal")).andExpect(status().isOk)
        Mockito.verify(diarioBordoService).listarResumoMensal(null, null, null)
    }

    @Test
    fun `edicao de veiculo sem tipoId e aceita`() {
        json(put("/veiculos/$uuid"), """{"descricao":"Caminhão"}""")
            .andExpect(status().isOk)
    }

    // ---------- Códigos de erro (bloqueios da Parte 5) ----------

    @Test
    fun `bloqueios trazem codigo alem de erro e mantem o status 400`() {
        given(diarioBordoService.abrirDiario(qualquer())).willThrow(
            RegraNegocioException("Cadastre seu PIN de assinatura antes de abrir um diário.", CodigosErro.PIN_AUSENTE))
        json(post("/diarios/abrir"), """{"loginMotorista":"m","veiculoId":"$uuid","medidorInicial":100}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Cadastre seu PIN de assinatura antes de abrir um diário."))
            .andExpect(jsonPath("$.codigo").value("PIN_AUSENTE"))

        given(turnoService.fecharTurno(TurnoRequest("m"))).willThrow(
            RegraNegocioException("Você tem um diário aberto. Feche o diário antes de sair de serviço.", CodigosErro.TURNO_COM_DIARIO_ABERTO))
        json(post("/turnos/fechar"), """{"login":"m"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("TURNO_COM_DIARIO_ABERTO"))
    }

    @Test
    fun `erro sem codigo nao ganha o campo`() {
        given(turnoService.abrirTurno(TurnoRequest("m"))).willThrow(RegraNegocioException("Já existe um turno aberto para este usuário."))
        json(post("/turnos/abrir"), """{"login":"m"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").doesNotExist())
    }

    // ---------- Parte 6B ----------

    @Test
    fun `reenvio simultaneo com o mesmo idLocal responde 409 na rota nova e 400 na antiga`() {
        val duplicado = DataIntegrityViolationException("x",
            SQLException("duplicate key value violates unique constraint \"uq_rastreamento_id_local\"", "23505"))
        given(rastreamentoService.registrarLote(qualquer())).willThrow(duplicado)
        json(post("/rastreamento/lote"), """{"diarioId":"$uuid","pontos":[{"latitude":1,"longitude":2}]}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("REENVIO_SIMULTANEO"))

        given(abastecimentoService.registrar(qualquer())).willThrow(duplicado)
        json(post("/abastecimentos"), """{"loginMotorista":"m","tipoCombustivel":"diesel","litros":10,"valorTotal":60}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("REENVIO_SIMULTANEO"))
    }

    @Test
    fun `fechar diario por id usa os status das rotas novas`() {
        given(diarioBordoService.fecharDiarioPorId(anyString(), qualquer()))
            .willThrow(ConflitoException("Este diário já foi fechado.", "DIARIO_JA_FECHADO"))
        json(post("/diarios/$uuid/fechar"), """{"medidorFinal":10}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("DIARIO_JA_FECHADO"))
    }

    @Test
    fun `horario no futuro alem de 24 h responde 422 na rota nova`() {
        given(rastreamentoService.registrarLote(qualquer()))
            .willThrow(com.example.sistemaobras.exception.NaoProcessavelException("Horário no futuro.", "HORARIO_FUTURO"))
        json(post("/rastreamento/lote"), """{"diarioId":"$uuid","pontos":[]}""")
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.codigo").value("HORARIO_FUTURO"))
    }

    // ---------- Parte 9: fotos ----------

    @Test
    fun `envio de foto e multipart e os erros saem com o status das rotas novas`() {
        val arquivo = org.springframework.mock.web.MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        given(fotoService.enviar(qualquer(), qualquer())).willThrow(
            com.example.sistemaobras.exception.ApiException(
                org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A foto precisa ser JPEG ou PNG.", "TIPO_ARQUIVO_INVALIDO"
            )
        )
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/fotos")
            .file(arquivo).param("tipo", "livre").param("diarioId", uuid))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.codigo").value("TIPO_ARQUIVO_INVALIDO"))
    }

    @Test
    fun `envio de foto sem o arquivo responde 400 com mensagem`() {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/fotos")
            .param("tipo", "livre"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Parte obrigatória ausente: arquivo."))
    }

    @Test
    fun `imagem sai com content-type, cache privado e etag, e 304 se nao mudou`() {
        given(fotoService.lerImagem(uuid)).willReturn(FotoService.Imagem(byteArrayOf(9, 8, 7), "image/png", "abc123"))
        mockMvc.perform(get("/fotos/$uuid"))
            .andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "image/png"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("ETag", "\"abc123\""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(byteArrayOf(9, 8, 7)))
        mockMvc.perform(get("/fotos/$uuid").header("If-None-Match", "\"abc123\""))
            .andExpect(status().isNotModified)
    }

    @Test
    fun `fotos uso nao e confundido com id de foto`() {
        given(fotoService.uso()).willReturn(com.example.sistemaobras.dto.UsoFotosResponse(0, 0, 0.0, 0, 0.0, 0, 0.0))
        mockMvc.perform(get("/fotos/uso")).andExpect(status().isOk).andExpect(jsonPath("$.quantidadeFotos").value(0))
        Mockito.verify(fotoService, Mockito.never()).lerImagem("uso")
    }

    // ---------- Parte 9 (extra): motivos de uso ----------

    @Test
    fun `motivos de uso lista id e descricao dos ativos`() {
        given(motivoUsoRepository.findByAtivoTrueOrderByDescricao()).willReturn(listOf(
            com.example.sistemaobras.entity.MotivoUso(java.util.UUID.fromString(uuid), "Entrega de material", true)
        ))
        mockMvc.perform(get("/motivos-uso"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(uuid))
            .andExpect(jsonPath("$[0].descricao").value("Entrega de material"))
            .andExpect(jsonPath("$[0].ativo").doesNotExist())
    }

    @Test
    fun `sucesso continua com o mesmo status`() {
        json(post("/veiculos"), """{"tipoId":"$uuid","descricao":"Caminhão"}""")
            .andExpect(status().isCreated)
    }
}
