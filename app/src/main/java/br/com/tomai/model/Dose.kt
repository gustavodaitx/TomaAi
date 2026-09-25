package br.com.tomai.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Instância de dose programada para um dia, persistida em `/doses/{id}`.
 */
@IgnoreExtraProperties
data class Dose(
    @DocumentId
    val id: String = "",

    val usuarioId: String = "",

    val medicamentoId: String = "",

    val medicamentoNome: String = "",

    val dosagem: String = "",

    /** Data da agenda no formato ISO `yyyy-MM-dd`. */
    val dataAgenda: String = "",

    /** Horário programado `HH:mm`. */
    val horarioProgramado: String = "",

    val status: String = STATUS_PENDENTE,

    val confirmadaEm: Timestamp? = null,

    @ServerTimestamp
    val criadoEm: Timestamp? = null
) {
    val estaPendente: Boolean get() = status == STATUS_PENDENTE

    fun estaAtrasada(agora: LocalDateTime = LocalDateTime.now(ZONE_TOMAAI)): Boolean {
        if (status != STATUS_PENDENTE) return false
        val data = runCatching { LocalDate.parse(dataAgenda) }.getOrNull() ?: return false
        val hora = runCatching { LocalTime.parse(horarioProgramado) }.getOrNull() ?: return false
        val programado = LocalDateTime.of(data, hora)
        return agora.isAfter(programado)
    }

    companion object {
        const val STATUS_PENDENTE = "PENDENTE"
        const val STATUS_CONFIRMADA = "CONFIRMADA"
        const val STATUS_IGNORADA = "IGNORADA"
        const val STATUS_NAO_CONFIRMADA = "NAO_CONFIRMADA"

        private val formatoData = DateTimeFormatter.ISO_LOCAL_DATE

        fun idDeterministico(
            usuarioId: String,
            dataAgenda: String,
            medicamentoId: String,
            horarioProgramado: String
        ): String {
            val hora = horarioProgramado.replace(":", "")
            return "${usuarioId}_${dataAgenda}_${medicamentoId}_$hora"
        }

        private val ZONE_TOMAAI: ZoneId = ZoneId.of("America/Sao_Paulo")

        fun dataHoje(zoneId: ZoneId = ZONE_TOMAAI): String {
            return LocalDate.now(zoneId).format(formatoData)
        }

        fun fromFirestoreMap(id: String, data: Map<String, Any?>): Dose {
            return Dose(
                id = id,
                usuarioId = data["usuarioId"] as? String ?: "",
                medicamentoId = data["medicamentoId"] as? String ?: "",
                medicamentoNome = data["medicamentoNome"] as? String ?: "",
                dosagem = data["dosagem"] as? String ?: "",
                dataAgenda = data["dataAgenda"] as? String ?: "",
                horarioProgramado = data["horarioProgramado"] as? String ?: "",
                status = data["status"] as? String ?: STATUS_PENDENTE,
                confirmadaEm = data["confirmadaEm"] as? Timestamp,
                criadoEm = data["criadoEm"] as? Timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = buildMap {
        put("usuarioId", usuarioId)
        put("medicamentoId", medicamentoId)
        put("medicamentoNome", medicamentoNome)
        put("dosagem", dosagem)
        put("dataAgenda", dataAgenda)
        put("horarioProgramado", horarioProgramado)
        put("status", status)
        confirmadaEm?.let { put("confirmadaEm", it) }
    }
}
