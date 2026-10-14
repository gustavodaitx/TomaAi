package br.com.tomai.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Persiste somente metadados não sensíveis no perfil do usuário autenticado. */
class AssinaturaRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    suspend fun atualizarPlanoUsuario(usuarioId: String, planoId: String, metodo: String?) {
        require(usuarioId.isNotBlank())
        require(planoId in setOf("FREE", "QUINZENAL", "MENSAL"))
        val dados = mutableMapOf<String, Any?>(
            "planoAtual" to planoId,
            "planoAtualizadoEm" to FieldValue.serverTimestamp(),
            "origemPlano" to if (planoId == "FREE") "SELECAO_GRATUITA" else "SIMULACAO_PAGAMENTO",
            "statusSimulacao" to if (planoId == "FREE") null else "APROVADO_SIMULADO"
        )
        dados["metodoSimulacao"] = metodo
        firestore.collection("usuarios").document(usuarioId).update(dados).await()
    }
}
