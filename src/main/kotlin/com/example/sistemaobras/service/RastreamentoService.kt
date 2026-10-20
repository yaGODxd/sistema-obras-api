package com.example.sistemaobras.service

import com.example.sistemaobras.dto.PontoLoteRequest
import com.example.sistemaobras.dto.RastreamentoLoteRequest
import com.example.sistemaobras.dto.RastreamentoLoteResponse
import com.example.sistemaobras.dto.RastreamentoRequest
import com.example.sistemaobras.dto.ResultadoPontoResponse
import com.example.sistemaobras.exception.ApiException
import com.example.sistemaobras.exception.CodigosErro
import com.example.sistemaobras.exception.ConflitoException
import com.example.sistemaobras.exception.RegraNegocioException
import com.example.sistemaobras.exception.uuidValido
import com.example.sistemaobras.repository.DiarioBordoRepository
import com.example.sistemaobras.dto.RastreamentoResponse
import com.example.sistemaobras.dto.UltimaPosicaoResponse
import com.example.sistemaobras.repository.RastreamentoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class RastreamentoService(
    private val rastreamentoRepository: RastreamentoRepository,
    private val diarioRepository: DiarioBordoRepository,
    private val diarioDoEvento: DiarioDoEvento
) {

    companion object {
        const val MAXIMO_POR_LOTE = 200
        const val GRAVADO = "gravado"
        const val DUPLICADO = "duplicado"
        const val DESCARTADO = "descartado_proximo"
        const val RECUSADO = "recusado"
    }

    // POST /rastreamento (rota antiga). Com diarioId vale esse diário; sem ele, o diário aberto agora.
    // Sem diário aberto o ponto é ignorado (como antes). Recusas sobem como erro (HORARIO_FUTURO, ID_LOCAL_EM_USO).
    @Transactional
    fun registrarPonto(request: RastreamentoRequest) {
        val diario = if (!request.diarioId.isNullOrBlank())
            diarioDoEvento.porId(request.diarioId).also {
                diarioDoEvento.conferirDono(it)
                diarioDoEvento.conferirNaoAssinado(it)
            }
        else
            diarioRepository.findDiarioAberto(request.loginMotorista)?.let { diarioDoEvento.porId(it.id.toString()) }
                ?: return
        val ponto = PontoLoteRequest(request.latitude, request.longitude, request.velocidade, request.ocorridoEm, request.idLocal)
        gravar(diario, ponto, request.enviadoEm)
    }

    // POST /rastreamento/lote (rota nova). Diário de outro motorista: 403; assinado: 409.
    // Cada ponto tem seu resultado; um ponto recusado não derruba o lote.
    @Transactional
    fun registrarLote(request: RastreamentoLoteRequest): RastreamentoLoteResponse {
        if (request.pontos.size > MAXIMO_POR_LOTE)
            throw RegraNegocioException(
                "Envie no máximo $MAXIMO_POR_LOTE pontos por lote.", CodigosErro.LOTE_GRANDE_DEMAIS
            )
        val diario = diarioDoEvento.porId(request.diarioId)
        diarioDoEvento.conferirDono(diario)
        diarioDoEvento.conferirNaoAssinado(diario)

        val resultados = request.pontos.mapIndexed { i, ponto ->
            try {
                ResultadoPontoResponse(i, ponto.idLocal, gravar(diario, ponto, request.enviadoEm))
            } catch (e: ApiException) {
                ResultadoPontoResponse(i, ponto.idLocal, RECUSADO, e.codigo, e.message)
            }
        }
        return RastreamentoLoteResponse(
            diarioId = diario.id.toString(),
            recebidos = resultados.size,
            gravados = resultados.count { it.resultado == GRAVADO },
            duplicados = resultados.count { it.resultado == DUPLICADO },
            descartados = resultados.count { it.resultado == DESCARTADO },
            recusados = resultados.count { it.resultado == RECUSADO },
            pontos = resultados
        )
    }

    // Grava um ponto e devolve o resultado. Lança ApiException quando o ponto é recusado.
    private fun gravar(diario: DiarioDoEvento.Diario, ponto: PontoLoteRequest, enviadoEm: String?): String {
        val idLocal = ponto.idLocal?.takeIf { it.isNotBlank() }?.let { uuidValido(it) }
        if (idLocal != null) {
            rastreamentoRepository.buscarPorIdLocal(idLocal.toString()).firstOrNull()?.let {
                if (it[1].toString() == diario.id.toString()) return DUPLICADO
                throw ConflitoException("Este idLocal já foi usado em outro registro.", CodigosErro.ID_LOCAL_EM_USO)
            }
        }
        val horario = HorarioEvento.resolver(ponto.ocorridoEm, null, enviadoEm)
            .conferirJanela(diario.abertoEm, diario.fechadoEm, CodigosErro.FORA_DA_JANELA_DIARIO)

        if (rastreamentoRepository.contarProximosNoDiario(diario.id.toString(), ponto.latitude, ponto.longitude, horario.ocorrido) > 0)
            return DESCARTADO

        rastreamentoRepository.inserirPonto(
            diarioId = diario.id.toString(),
            latitude = ponto.latitude,
            longitude = ponto.longitude,
            velocidade = ponto.velocidade,
            registradoEm = horario.ocorrido,
            recebidoEm = horario.recebido,
            idLocal = idLocal?.toString(),
            desvio = horario.desvioSeg,
            suspeito = horario.suspeito,
            motivo = horario.motivoTexto
        )
        return GRAVADO
    }

    fun buscarPontosPorDiario(diarioId: String): List<RastreamentoResponse> {
        return rastreamentoRepository.findByDiarioId(diarioId).map { r ->
            RastreamentoResponse(
                id = r.id,
                diarioId = r.diarioId,
                latitude = r.latitude.toDouble(),
                longitude = r.longitude.toDouble(),
                velocidade = r.velocidade?.toDouble(),
                registradoEm = r.registradoEm,
                recebidoEm = r.recebidoEm,
                idLocal = r.idLocal,
                horarioSuspeito = r.horarioSuspeito,
                motivoSuspeita = r.motivoSuspeita
            )
        }
    }
    // Atualizado: mapeia os 9 campos retornados pela query (índices 0–8)
    fun buscarUltimasPosicoes(): List<UltimaPosicaoResponse> {
        return rastreamentoRepository.findUltimasPosicoes().map { row ->
            UltimaPosicaoResponse(
                diarioId   = row[0]?.let { UUID.fromString(it.toString()) },
                login      = row[1].toString(),
                nomeCompleto = row[2].toString(),
                latitude   = row[3].toString().toDouble(),
                longitude  = row[4].toString().toDouble(),
                registradoEm = row[5]?.let {
                    LocalDateTime.parse(it.toString().replace(" ", "T"))
                },
                veiculoDescricao = row[6]?.toString(),
                veiculoPlaca     = row[7]?.toString(),
                destino          = row[8]?.toString()
            )
        }
    }
}