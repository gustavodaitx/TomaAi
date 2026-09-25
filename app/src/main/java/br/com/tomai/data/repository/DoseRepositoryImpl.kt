package br.com.tomai.data.repository

import android.util.Log
import br.com.tomai.model.Dose
import br.com.tomai.model.Medicamento
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class DoseRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : DoseRepository {

    companion object {
        const val COLECAO_DOSES = "doses"
        private const val TAG = "TomaAi_DoseRepo"
    }

    override fun observarDosesDoDia(usuarioId: String, dataAgenda: String): Flow<List<Dose>> =
        callbackFlow {
            if (usuarioId.isBlank() || dataAgenda.isBlank()) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }

            val registration = firestore.collection(COLECAO_DOSES)
                .whereEqualTo("usuarioId", usuarioId)
                .whereEqualTo("dataAgenda", dataAgenda)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "observarDosesDoDia: ${error.message}", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val lista = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            Dose.fromFirestoreMap(doc.id, doc.data ?: emptyMap())
                        } catch (e: Exception) {
                            Log.w(TAG, "observarDosesDoDia: map ${doc.id}: ${e.message}")
                            null
                        }
                    }?.sortedWith(
                        compareBy<Dose> { it.horarioProgramado }
                            .thenBy { it.medicamentoNome.lowercase() }
                    ) ?: emptyList()
                    trySend(lista)
                }

            awaitClose { registration.remove() }
        }

    override suspend fun garantirAgendaDiaria(
        usuarioId: String,
        medicamentos: List<Medicamento>,
        dataAgenda: String
    ): Result<Unit> {
        return try {
            if (usuarioId.isBlank()) {
                return Result.failure(IllegalStateException("Usuário não identificado."))
            }
            val batch = firestore.batch()
            var operacoes = 0

            medicamentos.filter { it.ativo }.forEach { medicamento ->
                medicamento.horarios.forEach { horario ->
                    if (horario.hora.isBlank()) return@forEach
                    val doseId = Dose.idDeterministico(
                        usuarioId = usuarioId,
                        dataAgenda = dataAgenda,
                        medicamentoId = medicamento.id,
                        horarioProgramado = horario.hora
                    )
                    val docRef = firestore.collection(COLECAO_DOSES).document(doseId)
                    val snapshot = docRef.get().await()
                    if (!snapshot.exists()) {
                        val payload = mapOf(
                            "usuarioId" to usuarioId,
                            "medicamentoId" to medicamento.id,
                            "medicamentoNome" to medicamento.nome,
                            "dosagem" to medicamento.dosagem,
                            "dataAgenda" to dataAgenda,
                            "horarioProgramado" to horario.hora,
                            "status" to Dose.STATUS_PENDENTE,
                            "criadoEm" to FieldValue.serverTimestamp()
                        )
                        batch.set(docRef, payload)
                        operacoes++
                    } else {
                        val meta = mapOf(
                            "medicamentoNome" to medicamento.nome,
                            "dosagem" to medicamento.dosagem
                        )
                        batch.set(docRef, meta, SetOptions.merge())
                        operacoes++
                    }
                }
            }

            if (operacoes > 0) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "garantirAgendaDiaria: ${e.message}", e)
            Result.failure(Exception(mapearErro(e)))
        }
    }

    override suspend fun atualizarStatus(
        doseId: String,
        novoStatus: String,
        medicamentoId: String?
    ): Result<Dose> {
        return try {
            if (doseId.isBlank()) {
                return Result.failure(IllegalArgumentException("Dose inválida."))
            }
            val docRef = firestore.collection(COLECAO_DOSES).document(doseId)
            val atualizacao = mutableMapOf<String, Any?>(
                "status" to novoStatus
            )
            if (novoStatus == Dose.STATUS_CONFIRMADA) {
                atualizacao["confirmadaEm"] = Timestamp.now()
            }
            docRef.set(atualizacao, SetOptions.merge()).await()

            if (novoStatus == Dose.STATUS_CONFIRMADA && !medicamentoId.isNullOrBlank()) {
                decrementarEstoque(medicamentoId)
            }

            val snapshot = docRef.get().await()
            val dose = Dose.fromFirestoreMap(snapshot.id, snapshot.data ?: emptyMap())
            Result.success(dose)
        } catch (e: Exception) {
            Log.e(TAG, "atualizarStatus: ${e.message}", e)
            Result.failure(Exception(mapearErro(e)))
        }
    }

    private suspend fun decrementarEstoque(medicamentoId: String) {
        val medRef = firestore.collection(MedicamentoRepositoryImpl.COLECAO_MEDICAMENTOS)
            .document(medicamentoId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(medRef)
            val atual = (snapshot.getDouble("estoqueAtual") ?: snapshot.getLong("estoqueAtual")?.toDouble())
                ?: 0.0
            val novo = (atual - 1).coerceAtLeast(0.0)
            transaction.update(medRef, "estoqueAtual", novo.toInt())
        }.await()
    }

    private fun mapearErro(exception: Exception): String {
        return exception.localizedMessage
            ?: "Não foi possível atualizar a dose. Tente novamente."
    }
}
