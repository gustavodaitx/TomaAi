package br.com.tomai.data.repository

import android.util.Log
import br.com.tomai.model.Plano
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class DadosCartaoContratacao(
    val nomeTitular: String,
    val numero: String,
    val validadeMes: String,
    val validadeAno: String,
    val cvv: String,
    val cpf: String,
    val cep: String,
    val numeroEndereco: String,
    val telefone: String
)

class AssinaturaRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun listarPlanos(): List<Plano> {
        val snapshot = firestore.collection("planos").get().await()
        return snapshot.documents.mapNotNull { document ->
            runCatching {
                Plano.fromMap(document.id, document.data.orEmpty())
            }.onFailure { erro ->
                Log.w("AssinaturaRepository", "Plano ${document.id} ignorado: ${erro.localizedMessage}")
            }.getOrNull()
        }
    }

    suspend fun criarAssinatura(
        planoId: String,
        formaPagamento: String,
        cartao: DadosCartaoContratacao? = null
    ) = functions.getHttpsCallable("criarAssinaturaAsaas")
        .call(buildMap {
            put("planoId", planoId)
            put("formaPagamento", formaPagamento)
            cartao?.let {
                put("dadosCartao", mapOf(
                    "nomeTitular" to it.nomeTitular,
                    "numero" to it.numero,
                    "validadeMes" to it.validadeMes,
                    "validadeAno" to it.validadeAno,
                    "cvv" to it.cvv,
                    "cpf" to it.cpf,
                    "cep" to it.cep,
                    "numeroEndereco" to it.numeroEndereco,
                    "telefone" to it.telefone
                ))
            }
        }).await()

    suspend fun consultarCobrancas(assinaturaId: String) = functions
        .getHttpsCallable("consultarCobrancas")
        .call(mapOf("assinaturaId" to assinaturaId)).await()

    suspend fun cancelarAssinatura(assinaturaId: String) = functions
        .getHttpsCallable("cancelarAssinaturaAsaas")
        .call(mapOf("assinaturaId" to assinaturaId)).await()

    suspend fun obterPix(cobrancaId: String): Pair<String, String?> {
        val result = functions.getHttpsCallable("obterPixCobranca")
            .call(mapOf("cobrancaId" to cobrancaId)).await()
        val data = result.getData() as? Map<*, *>
        val payload = data?.get("payload") as? String
            ?: throw IllegalStateException("Código Pix não disponível para esta cobrança.")
        return payload to (data["encodedImage"] as? String)
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
