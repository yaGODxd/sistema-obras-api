package com.example.sistemaobras.service

import com.example.sistemaobras.dto.RastreamentoRequest
import com.example.sistemaobras.dto.RastreamentoResponse
import com.example.sistemaobras.dto.UltimaPosicaoResponse
import com.example.sistemaobras.repository.RastreamentoRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class RastreamentoService(
    private val rastreamentoRepository: RastreamentoRepository
) {

    fun registrarPonto(request: RastreamentoRequest) {
        val proximos = rastreamentoRepository.contarPontosProximos(
            request.loginMotorista, request.latitude, request.longitude
        )
        if (proximos > 0) return

        rastreamentoRepository.inserirPonto(
            login = request.loginMotorista,
            latitude = request.latitude,
            longitude = request.longitude,
            velocidade = request.velocidade
        )
    }

    fun buscarPontosPorDiario(diarioId: String): List<RastreamentoResponse> {
        return rastreamentoRepository.findByDiarioId(diarioId).map { r ->
            RastreamentoResponse(
                id = r.id,
                diarioId = r.diarioId,
                latitude = r.latitude.toDouble(),
                longitude = r.longitude.toDouble(),
                velocidade = r.velocidade?.toDouble(),
                registradoEm = r.registradoEm
            )
        }
    }

    fun buscarUltimasPosicoes(): List<UltimaPosicaoResponse> {
        return rastreamentoRepository.findUltimasPosicoes().map { row ->
            UltimaPosicaoResponse(
                login = row[0].toString(),
                nomeCompleto = row[1].toString(),
                latitude = row[2].toString().toDouble(),
                longitude = row[3].toString().toDouble(),
                registradoEm = if (row[4] != null)
                    LocalDateTime.parse(row[4].toString().replace(" ", "T"))
                else null,
                veiculoDescricao = row[5]?.toString()
            )
        }
    }
}