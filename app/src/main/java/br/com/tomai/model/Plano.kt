package br.com.tomai.model



import com.google.firebase.firestore.PropertyName

data class Plano(
    val id: String = "",
    val nome: String = "",
    val descricao: String = "",
    val valor: Double = 0.0,
    val ciclo: String = "",
    val diasRecorrencia: Int = 0,
    @get:PropertyName("ativo") @set:PropertyName("ativo")
    var ativo: Boolean = true
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): Plano = Plano(
            id = id,
            nome = data["nome"] as? String ?: id,
            descricao = data["descricao"] as? String ?: "",
            valor = (data["valor"] as? Number)?.toDouble() ?: 0.0,
            ciclo = data["ciclo"] as? String ?: id,
            diasRecorrencia = ((data["dias"] ?: data["diasRecorrencia"]) as? Number)?.toInt() ?: 0,
            ativo = data["ativo"] as? Boolean ?: true
        )
    }
}
