package br.com.tomai.model

/**
 * Horário recorrente associado a um [Medicamento].
 * Persistido como lista embarcada no documento Firestore do medicamento.
 */
data class HorarioMedicamento(
    val hora: String = "",
    val rotulo: String? = null
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("hora", hora)
        if (!rotulo.isNullOrBlank()) {
            put("rotulo", rotulo.trim())
        }
    }

    companion object {
        fun fromMap(map: Map<*, *>): HorarioMedicamento {
            return HorarioMedicamento(
                hora = (map["hora"] as? String).orEmpty(),
                rotulo = map["rotulo"] as? String
            )
        }
    }
}
