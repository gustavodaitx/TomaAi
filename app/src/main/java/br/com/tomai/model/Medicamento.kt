package br.com.tomai.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Medicamento do paciente, armazenado em `/medicamentos/{id}` com [usuarioId] = uid do Auth.
 */
@IgnoreExtraProperties
data class Medicamento(
    @DocumentId
    val id: String = "",

    val usuarioId: String = "",

    val nome: String = "",

    val dosagem: String = "",

    val frequencia: String = FREQUENCIA_DIARIA,

    val dataInicio: String = "",

    val dataFim: String = "",

    val diasSemana: List<Int> = emptyList(),

    val horarios: List<HorarioMedicamento> = emptyList(),

    val estoqueInicial: Int = 0,

    val estoqueAtual: Int = 0,

    val ativo: Boolean = true,

    @ServerTimestamp
    val criadoEm: Timestamp? = null,

    val atualizadoEm: Timestamp? = null
) {
    companion object {
        const val FREQUENCIA_DIARIA = "DIARIA"
        const val FREQUENCIA_PERSONALIZADA = "PERSONALIZADA"

        val FREQUENCIAS_DISPONIVEIS = listOf(FREQUENCIA_DIARIA, FREQUENCIA_PERSONALIZADA)

        fun fromFirestoreMap(
            id: String,
            data: Map<String, Any?>
        ): Medicamento {
            val horariosRaw = data["horarios"]
            val horarios = when (horariosRaw) {
                is List<*> -> horariosRaw.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> HorarioMedicamento.fromMap(item)
                        is String -> HorarioMedicamento(hora = item)
                        else -> null
                    }
                }
                else -> emptyList()
            }

            return Medicamento(
                id = id,
                usuarioId = data["usuarioId"] as? String ?: "",
                nome = data["nome"] as? String ?: "",
                dosagem = data["dosagem"] as? String ?: "",
                frequencia = data["frequencia"] as? String ?: FREQUENCIA_DIARIA,
                dataInicio = data["dataInicio"] as? String ?: "",
                dataFim = data["dataFim"] as? String ?: "",
                diasSemana = (data["diasSemana"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
                horarios = horarios,
                estoqueInicial = (data["estoqueInicial"] as? Number)?.toInt() ?: 0,
                estoqueAtual = (data["estoqueAtual"] as? Number)?.toInt()
                    ?: (data["estoqueInicial"] as? Number)?.toInt()
                    ?: 0,
                ativo = when (val valor = data["ativo"]) {
                    is Boolean -> valor
                    is Number -> valor.toInt() != 0
                    else -> true
                },
                criadoEm = data["criadoEm"] as? Timestamp,
                atualizadoEm = data["atualizadoEm"] as? Timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "usuarioId" to usuarioId,
        "nome" to nome.trim(),
        "dosagem" to dosagem.trim(),
        "frequencia" to frequencia,
        "dataInicio" to dataInicio,
        "dataFim" to dataFim,
        "diasSemana" to diasSemana,
        "horarios" to horarios.map { it.toMap() },
        "estoqueInicial" to estoqueInicial,
        "estoqueAtual" to estoqueAtual,
        "ativo" to ativo,
        "atualizadoEm" to Timestamp.now()
    )
}
