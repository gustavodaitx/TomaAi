package br.com.tomai.data.repository

import android.util.Log
import br.com.tomai.model.Medicamento
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MedicamentoRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : MedicamentoRepository {

    companion object {
        const val COLECAO_MEDICAMENTOS = "medicamentos"
        private const val TAG = "TomaAi_MedicamentoRepo"
    }

    override fun observarMedicamentos(usuarioId: String): Flow<List<Medicamento>> = callbackFlow {
        if (usuarioId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        registration = firestore.collection(COLECAO_MEDICAMENTOS)
            .whereEqualTo("usuarioId", usuarioId)
            .whereEqualTo("ativo", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observarMedicamentos: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val lista = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val map = doc.data ?: return@mapNotNull null
                        Medicamento.fromFirestoreMap(doc.id, map)
                    } catch (e: Exception) {
                        Log.w(TAG, "observarMedicamentos: falha ao mapear ${doc.id}: ${e.message}")
                        null
                    }
                }?.sortedBy { it.nome.lowercase() } ?: emptyList()
                trySend(lista)
            }

        awaitClose {
            registration?.remove()
        }
    }

    override suspend fun buscarPorId(medicamentoId: String): Result<Medicamento?> {
        return try {
            if (medicamentoId.isBlank()) {
                return Result.success(null)
            }
            val snapshot = firestore.collection(COLECAO_MEDICAMENTOS)
                .document(medicamentoId)
                .get()
                .await()
            if (!snapshot.exists()) {
                Result.success(null)
            } else {
                val data = snapshot.data ?: emptyMap()
                Result.success(Medicamento.fromFirestoreMap(snapshot.id, data))
            }
        } catch (e: Exception) {
            Log.e(TAG, "buscarPorId: ${e.message}", e)
            Result.failure(Exception(mapearErro(e)))
        }
    }

    override suspend fun salvar(medicamento: Medicamento): Result<Medicamento> {
        return try {
            if (medicamento.usuarioId.isBlank()) {
                return Result.failure(IllegalStateException("Usuário não identificado para salvar medicamento."))
            }
            if (medicamento.nome.isBlank()) {
                return Result.failure(IllegalArgumentException("O nome do medicamento é obrigatório."))
            }

            val colecao = firestore.collection(COLECAO_MEDICAMENTOS)
            val docRef = if (medicamento.id.isBlank()) {
                colecao.document()
            } else {
                colecao.document(medicamento.id)
            }

            val estoqueInicial = medicamento.estoqueInicial.coerceAtLeast(0)
            val estoqueAtual = if (medicamento.id.isBlank()) {
                estoqueInicial
            } else {
                medicamento.estoqueAtual.coerceAtLeast(0)
            }

            val payload = medicamento.copy(
                id = docRef.id,
                estoqueInicial = estoqueInicial,
                estoqueAtual = estoqueAtual,
                ativo = true
            ).toMap().toMutableMap()

            if (medicamento.id.isBlank()) {
                payload["criadoEm"] = Timestamp.now()
            }

            docRef.set(payload, SetOptions.merge()).await()
            val salvo = Medicamento.fromFirestoreMap(docRef.id, payload)
            Result.success(salvo)
        } catch (e: Exception) {
            Log.e(TAG, "salvar: ${e.message}", e)
            Result.failure(Exception(mapearErro(e)))
        }
    }

    override suspend fun desativar(medicamentoId: String): Result<Unit> {
        return try {
            if (medicamentoId.isBlank()) {
                return Result.failure(IllegalArgumentException("Medicamento inválido."))
            }
            firestore.collection(COLECAO_MEDICAMENTOS)
                .document(medicamentoId)
                .set(
                    mapOf(
                        "ativo" to false,
                        "atualizadoEm" to Timestamp.now()
                    ),
                    SetOptions.merge()
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "desativar: ${e.message}", e)
            Result.failure(Exception(mapearErro(e)))
        }
    }

    private fun mapearErro(exception: Exception): String {
        return exception.localizedMessage
            ?: "Não foi possível concluir a operação. Tente novamente."
    }
}
