package br.com.tomai.data.repository

import br.com.tomai.model.Plano
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AssinaturaRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun listarPlanos(): List<Plano> {
        val result = functions.getHttpsCallable("listarPlanos").call().await()
        val rows = result.getData() as? List<*> ?: return emptyList()
        return rows.filterIsInstance<Map<*, *>>().mapNotNull { row ->
            val data = row.entries.filter { it.key is String }.associate { it.key as String to it.value }
            val id = data["id"] as? String ?: return@mapNotNull null
            Plano.fromMap(id, data)
        }
    }

    suspend fun criarAssinatura(planoId: String, formaPagamento: String) = functions
        .getHttpsCallable("criarAssinaturaAsaas")
        .call(mapOf("planoId" to planoId, "formaPagamento" to formaPagamento)).await()

    suspend fun consultarCobrancas(assinaturaId: String) = functions
        .getHttpsCallable("consultarCobrancas")
        .call(mapOf("assinaturaId" to assinaturaId)).await()

    suspend fun cancelarAssinatura(assinaturaId: String) = functions
        .getHttpsCallable("cancelarAssinaturaAsaas")
        .call(mapOf("assinaturaId" to assinaturaId)).await()

    suspend fun obterPix(cobrancaId: String): String {
        val result = functions.getHttpsCallable("obterPixCobranca")
            .call(mapOf("cobrancaId" to cobrancaId)).await()
        return (result.getData() as? Map<*, *>)?.get("payload") as? String
            ?: throw IllegalStateException("Código Pix não disponível para esta cobrança.")
    }

    fun observarAssinaturas(usuarioId: String): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection("assinaturas").whereEqualTo("usuarioId", usuarioId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents?.map { it.data.orEmpty() + ("documentId" to it.id) }.orEmpty())
            }
        awaitClose { listener.remove() }
    }

    fun observarCobrancas(usuarioId: String): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection("cobrancas").whereEqualTo("usuarioId", usuarioId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents?.map { it.data.orEmpty() + ("documentId" to it.id) }.orEmpty())
            }
        awaitClose { listener.remove() }
    }
}
