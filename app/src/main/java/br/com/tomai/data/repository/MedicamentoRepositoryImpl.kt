package br.com.tomai.data.repository

import android.util.Log
import br.com.tomai.model.Dose
import br.com.tomai.model.Medicamento
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MedicamentoRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : MedicamentoRepository {

    companion object {
        const val COLECAO_MEDICAMENTOS = "medicamentos"
        private const val COLECAO_HORARIOS = "horarios_medicamentos"
        private const val COLECAO_DOSES = "doses"
        private const val COLECAO_ALERTAS = "alertas"
        private const val TAG = "TomaAi_MedicamentoRepo"
        private const val LIMITE_LOTE_FIRESTORE = 450
        private const val LIMITE_LOTE_ALERTAS_LEGADOS = 10
    }

    private val pacienteVinculadoResolver = PacienteVinculadoResolver(firestore, auth)

    override fun observarMedicamentos(usuarioId: String): Flow<List<Medicamento>> = callbackFlow {
        if (usuarioId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val pacienteUid = try {
            pacienteVinculadoResolver.resolverUidPaciente(usuarioId)
        } catch (e: Exception) {
            Log.e(TAG, "observarMedicamentos: não foi possível resolver o paciente", e)
            close(e)
            return@callbackFlow
        }
        if (pacienteUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = firestore.collection(COLECAO_MEDICAMENTOS)
            .whereEqualTo("usuarioId", pacienteUid)
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

        awaitClose { registration.remove() }
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

    override suspend fun excluir(medicamentoId: String): Result<MedicamentoRepository.ResultadoExclusao> {
        var etapa = "validar a sessão autenticada"
        var colecaoAtual = COLECAO_MEDICAMENTOS
        var documentoAtual = medicamentoId
        var uidParaLog = "desconhecido"
        return try {
            if (medicamentoId.isBlank()) {
                return Result.failure(IllegalArgumentException("Medicamento inválido."))
            }
            val uid = auth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("Sessão expirada. Faça login novamente."))
            uidParaLog = uid
            Log.i(TAG, "exclusao_inicio uid=$uid medicamentoId=$medicamentoId")

            val medicamentosRef = firestore.collection(COLECAO_MEDICAMENTOS)
            colecaoAtual = COLECAO_MEDICAMENTOS
            etapa = "confirmar a propriedade do medicamento"
            val medicamentoSnapshot = medicamentosRef
                .whereEqualTo("usuarioId", uid)
                .whereEqualTo(FieldPath.documentId(), medicamentoId)
                .get(Source.SERVER)
                .await()
            check(!medicamentoSnapshot.isEmpty) {
                "O medicamento não foi encontrado na conta autenticada. Nenhum registro foi excluído."
            }
            val medicamentoRef = medicamentosRef.document(medicamentoId)
            val horariosEmbutidos = Medicamento.fromFirestoreMap(
                medicamentoId,
                medicamentoSnapshot.documents.single().data ?: emptyMap()
            ).horarios.size
            Log.i(
                TAG,
                "exclusao_medicamento_proprietario_confirmado uid=$uid medicamentoId=$medicamentoId " +
                    "horariosEmbutidos=$horariosEmbutidos"
            )

            // Tombstone blocks dose generation while child records are removed.
            // The document stays visible, so a failed partial cleanup can be retried.
            etapa = "bloquear a geração de novas doses"
            colecaoAtual = COLECAO_MEDICAMENTOS
            medicamentoRef.update("exclusaoEmAndamento", true).await()
            check(medicamentoRef.get(Source.SERVER).await().getBoolean("exclusaoEmAndamento") == true) {
                "Não foi possível confirmar o bloqueio da geração de doses."
            }
            Log.i(TAG, "exclusao_bloqueio_geracao confirmado uid=$uid medicamentoId=$medicamentoId")

            val horariosRef = firestore.collection(COLECAO_HORARIOS)
            colecaoAtual = COLECAO_HORARIOS
            etapa = "consultar os horários do medicamento"
            val horarios = horariosRef
                .whereEqualTo("usuarioId", uid)
                .whereEqualTo("medicamentoId", medicamentoId)
                .get(Source.SERVER)
                .await()
                .documents
                .map { it.reference }
            Log.i(TAG, "exclusao_consulta colecao=$COLECAO_HORARIOS medicamentoId=$medicamentoId encontrados=${horarios.size}")

            val dosesRef = firestore.collection(COLECAO_DOSES)
            val instanteInicial = Instant.now()
            colecaoAtual = COLECAO_DOSES
            etapa = "consultar as doses do medicamento"
            val dosesRelacionadas = dosesRef
                .whereEqualTo("usuarioId", uid)
                .whereEqualTo("medicamentoId", medicamentoId)
                .get(Source.SERVER)
                .await()
                .documents
                .map { documento ->
                    val dose = runCatching {
                        Dose.fromFirestoreMap(documento.id, documento.data ?: emptyMap())
                    }.getOrElse { throw IllegalStateException("Não foi possível verificar a dose ${documento.id}.", it) }
                    if (dose.status == Dose.STATUS_PENDENTE &&
                        (runCatching { LocalDate.parse(dose.dataAgenda) }.isFailure ||
                            runCatching { LocalTime.parse(dose.horarioProgramado) }.isFailure)
                    ) {
                        throw IllegalStateException(
                            "Não foi possível determinar se a dose ${documento.id} é futura; nenhuma exclusão foi iniciada."
                        )
                    }
                    documento.reference to dose
                }
            val dosesFuturasPendentes = dosesRelacionadas.filter { (_, dose) ->
                ehDosePendenteFutura(dose, instanteInicial)
            }
            val dosesConfirmadas = dosesRelacionadas.count { (_, dose) -> dose.status == Dose.STATUS_CONFIRMADA }
            val dosesPassadas = dosesRelacionadas.count { (_, dose) ->
                val data = runCatching { LocalDate.parse(dose.dataAgenda) }.getOrNull()
                val hora = runCatching { LocalTime.parse(dose.horarioProgramado) }.getOrNull()
                data != null && hora != null &&
                    LocalDateTime.of(data, hora).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().isBefore(instanteInicial)
            }
            val dosesPreservadas = dosesRelacionadas.size - dosesFuturasPendentes.size
            Log.i(
                TAG,
                "exclusao_diagnostico uid=$uid medicamentoId=$medicamentoId " +
                    "dosesEncontradas=${dosesRelacionadas.size} dosesPendentesFuturas=${dosesFuturasPendentes.size} " +
                    "dosesConfirmadasPreservadas=$dosesConfirmadas dosesPassadasPreservadas=$dosesPassadas " +
                    "dosesPreservadasTotal=$dosesPreservadas"
            )

            // Alert documents are keyed by dose ID. Validate every linked record
            // before mutating anything; mismatched ownership aborts safely.
            val alertasProprios = mutableListOf<DocumentReference>()
            val alertasLegados = mutableListOf<DocumentReference>()
            for (lote in dosesFuturasPendentes.map { it.second.id }.chunked(10)) {
                colecaoAtual = COLECAO_ALERTAS
                etapa = "consultar os alertas das doses futuras"
                val snapshots = lote.map { doseId ->
                    documentoAtual = doseId
                    firestore.collection(COLECAO_ALERTAS).document(doseId).get(Source.SERVER).await()
                }
                snapshots.forEach { alerta ->
                    if (!alerta.exists()) return@forEach
                    when (val dono = alerta.getString("usuarioId")) {
                        uid -> alertasProprios.add(alerta.reference)
                        null -> alertasLegados.add(alerta.reference)
                        else -> error("Alerta relacionado a uma dose tem proprietario diferente ($dono).")
                    }
                }
            }

            etapa = "excluir os alertas das doses futuras"
            colecaoAtual = COLECAO_ALERTAS
            excluirEmLotes(alertasProprios, LIMITE_LOTE_FIRESTORE, "alertas")
            excluirEmLotes(alertasLegados, LIMITE_LOTE_ALERTAS_LEGADOS, "alertas legados")
            Log.i(TAG, "exclusao_etapa_sucesso colecao=$COLECAO_ALERTAS removidos=${alertasProprios.size + alertasLegados.size}")
            etapa = "confirmar a exclusão dos alertas"
            (alertasProprios + alertasLegados).forEach { alertaRef ->
                check(!alertaRef.get(Source.SERVER).await().exists()) {
                    "Um alerta relacionado permaneceu no servidor."
                }
            }
            Log.i(TAG, "exclusao_confirmacao colecao=$COLECAO_ALERTAS resultado=sem_alertas_relacionados")

            etapa = "excluir os horários do medicamento"
            colecaoAtual = COLECAO_HORARIOS
            excluirEmLotes(horarios, LIMITE_LOTE_FIRESTORE, "horários")
            Log.i(TAG, "exclusao_etapa_sucesso colecao=$COLECAO_HORARIOS removidos=${horarios.size}")
            etapa = "confirmar a exclusão dos horários"
            check(horariosRef
                .whereEqualTo("usuarioId", uid)
                .whereEqualTo("medicamentoId", medicamentoId)
                .get(Source.SERVER).await().isEmpty
            ) { "Ainda existem horários associados ao medicamento." }
            Log.i(TAG, "exclusao_confirmacao colecao=$COLECAO_HORARIOS resultado=sem_registros_relacionados")

            etapa = "excluir as doses pendentes futuras"
            colecaoAtual = COLECAO_DOSES
            excluirEmLotes(dosesFuturasPendentes.map { it.first }, LIMITE_LOTE_FIRESTORE, "doses futuras")
            Log.i(TAG, "exclusao_etapa_sucesso colecao=$COLECAO_DOSES removidas=${dosesFuturasPendentes.size}")
            etapa = "confirmar a exclusão das doses pendentes futuras"
            val dosesAposExclusao = dosesRef
                .whereEqualTo("usuarioId", uid)
                .whereEqualTo("medicamentoId", medicamentoId)
                .get(Source.SERVER)
                .await()
                .documents
                .map { documento ->
                    runCatching { Dose.fromFirestoreMap(documento.id, documento.data ?: emptyMap()) }
                        .getOrElse { throw IllegalStateException("Não foi possível confirmar a dose ${documento.id}.", it) }
                }
            check(dosesAposExclusao.none { ehDosePendenteFutura(it, Instant.now()) }) {
                "Ainda existem doses futuras pendentes associadas ao medicamento."
            }
            Log.i(
                TAG,
                "exclusao_confirmacao colecao=$COLECAO_DOSES " +
                    "pendentesFuturasRestantes=${dosesAposExclusao.count { ehDosePendenteFutura(it, Instant.now()) }} " +
                    "preservadas=${dosesAposExclusao.size}"
            )

            etapa = "excluir o medicamento"
            colecaoAtual = COLECAO_MEDICAMENTOS
            if (!medicamentoSnapshot.isEmpty) medicamentoRef.delete().await()
            Log.i(TAG, "exclusao_etapa_sucesso colecao=$COLECAO_MEDICAMENTOS documento=$medicamentoId")

            Result.success(
                MedicamentoRepository.ResultadoExclusao(
                    usuarioId = uid,
                    doseIdsRemovidas = dosesFuturasPendentes.map { it.second.id },
                    horariosRemovidos = horarios.size
                )
            )
        } catch (e: Exception) {
            val codigoErro = (e as? FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            Log.e(
                TAG,
                "exclusao_falha uid=$uidParaLog medicamentoId=$medicamentoId colecao=$colecaoAtual " +
                    "etapa=$etapa codigo=$codigoErro documento=$documentoAtual detalhe=${e.message}",
                e
            )
            val mensagem = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "O Firestore negou a etapa: $etapa. Verifique se as regras atualizadas foram publicadas no projeto correto."
            } else {
                "Falha ao $etapa: ${mapearErro(e)}"
            }
            Result.failure(Exception(mensagem, e))
        }
    }

    private suspend fun excluirEmLotes(
        referencias: List<DocumentReference>,
        limite: Int,
        etapa: String
    ) {
        referencias.chunked(limite).forEach { lote ->
            val batch = firestore.batch()
            lote.forEach { referencia -> batch.delete(referencia) }
            try {
                batch.commit().await()
                Log.i(TAG, "exclusao_lote_sucesso colecao=${lote.firstOrNull()?.parent?.id ?: etapa} quantidade=${lote.size}")
            } catch (erro: Exception) {
                val codigoErro = (erro as? FirebaseFirestoreException)?.code?.name ?: erro.javaClass.simpleName
                lote.forEach { referencia ->
                    Log.e(
                        TAG,
                        "exclusao_documento_falha colecao=${referencia.parent.id} documento=${referencia.id} codigo=$codigoErro",
                        erro
                    )
                }
                throw erro
            }
        }
    }

    private fun ehDosePendenteFutura(dose: Dose, agora: Instant): Boolean = doseEhPendenteFutura(dose, agora)

    private fun mapearErro(exception: Exception): String {
        return exception.localizedMessage
            ?: "Não foi possível concluir a operação. Tente novamente."
    }
}

internal fun doseEhPendenteFutura(
    dose: Dose,
    agora: Instant,
    zona: ZoneId = ZoneId.of("America/Sao_Paulo")
): Boolean {
    if (dose.status != Dose.STATUS_PENDENTE) return false
    val data = runCatching { LocalDate.parse(dose.dataAgenda) }.getOrNull() ?: return false
    val hora = runCatching { LocalTime.parse(dose.horarioProgramado) }.getOrNull() ?: return false
    return LocalDateTime.of(data, hora).atZone(zona).toInstant().isAfter(agora)
}
