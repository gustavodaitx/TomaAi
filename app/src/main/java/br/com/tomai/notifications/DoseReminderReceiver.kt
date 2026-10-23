package br.com.tomai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import br.com.tomai.model.Medicamento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DoseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getStringExtra(EXTRA_DOSE_ID).orEmpty()
        val deviceUid = intent.getStringExtra(EXTRA_DEVICE_UID).orEmpty()
        val pacienteUid = intent.getStringExtra(EXTRA_PACIENTE_UID).orEmpty()
        val medicamentoId = intent.getStringExtra(EXTRA_MEDICAMENTO_ID).orEmpty()
        val dataAgenda = intent.getStringExtra(EXTRA_DATA_AGENDA).orEmpty()
        val doseSemDocumento = intent.getBooleanExtra(EXTRA_DOSE_SEM_DOCUMENTO, false)
        val nome = intent.getStringExtra(EXTRA_MEDICAMENTO_NOME).orEmpty()
        val dosagem = intent.getStringExtra(EXTRA_DOSAGEM).orEmpty()
        val horario = intent.getStringExtra(EXTRA_HORARIO).orEmpty()
        if (doseId.isBlank() || deviceUid.isBlank() || pacienteUid.isBlank() || medicamentoId.isBlank()) {
            Log.w("TOMAI_NOTIFICACOES", "Evento de lembrete ignorado por dados incompletos.")
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Arm the next one-shot alarm first so a slow/offline Firestore request
                // cannot silently terminate the repeat chain.
                DoseReminderScheduler.agendarRepeticao(context, deviceUid, doseId, nome, dosagem, horario)
                val doseRef = FirebaseFirestore.getInstance().collection("doses").document(doseId)
                val snapshot = try {
                    doseRef.get(Source.SERVER).await()
                } catch (error: Exception) {
                    // Keep local reminders usable offline; reconcile against the server on the next repeat.
                    Log.w(TAG, "Estado remoto da dose indisponível; mantendo o lembrete local.", error)
                    null
                }
                var dose = snapshot?.takeIf { it.exists() }?.data
                if (snapshot != null && dose == null && doseSemDocumento) {
                    // O agendamento local cobre os prÃ³ximos dias, enquanto o Firestore
                    // pode ainda nÃ£o ter materializado a dose futura. Valide o medicamento
                    // e mantenha o lembrete local para esta data, sem criar dados no servidor.
                    val medicamentoValido = runCatching {
                        medicamentoAindaValido(pacienteUid, medicamentoId, dataAgenda, horario)
                    }.onFailure { Log.w(TAG, "NÃ£o foi possÃ­vel validar o medicamento futuro no servidor.", it) }
                        .getOrNull()
                    val dataAgendada = runCatching { java.time.LocalDate.parse(dataAgenda) }.getOrNull()
                    val hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))
                    if (medicamentoValido == false || dataAgendada == null || dataAgendada.isBefore(hoje)) {
                        DoseReminderScheduler.cancelarRepeticao(context, deviceUid, doseId)
                        DoseNotificationHelper.cancelarLembrete(context, notificationId(deviceUid, doseId))
                        return@launch
                    }
                    dose = mapOf("usuarioId" to pacienteUid, "status" to STATUS_PENDENTE)
                }
                if (snapshot != null && dose?.get("status") != STATUS_PENDENTE) {
                    DoseReminderScheduler.cancelarRepeticao(context, deviceUid, doseId)
                    DoseNotificationHelper.cancelarLembrete(context, notificationId(deviceUid, doseId))
                    return@launch
                }
                if (dose != null) {
                    val doseParaValidacao = dose
                    val autorizado = runCatching { destinatarioAindaAutorizado(deviceUid, doseParaValidacao) }
                        .onFailure { Log.w(TAG, "Não foi possível confirmar o vínculo no servidor.", it) }
                        .getOrNull()
                    if (autorizado == false) {
                        DoseReminderScheduler.cancelarRepeticao(context, deviceUid, doseId)
                        DoseNotificationHelper.cancelarLembrete(context, notificationId(deviceUid, doseId))
                        return@launch
                    }
                }

                DoseNotificationHelper.exibirLembrete(
                    context = context,
                    notificationId = notificationId(deviceUid, doseId),
                    titulo = "Hora do medicamento",
                    mensagem = if (nome.isNotBlank()) {
                        listOf(nome, dosagem, horario).filter(String::isNotBlank).joinToString(" · ")
                    } else {
                        "Confira sua dose programada para $horario."
                    }
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun destinatarioAindaAutorizado(deviceUid: String, dose: Map<String, Any?>): Boolean {
        val patientUid = dose["usuarioId"] as? String ?: return false
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser?.uid != deviceUid) return false
        val firestore = FirebaseFirestore.getInstance()
        val profile = firestore.collection("usuarios").document(deviceUid).get(Source.SERVER).await()
        return when (profile.getString("perfil")) {
            "PACIENTE" -> deviceUid == patientUid
            "PESSOA_DE_CONFIANCA", "CUIDADOR" -> {
                val patientMatches = profile.getString("pacienteUid") == patientUid
                val accepted = profile.getBoolean("aceitouReceberAvisos") == true
                val link = firestore.collection("vinculos").document("${patientUid}_$deviceUid").get(Source.SERVER).await()
                patientMatches && accepted && link.getBoolean("ativo") == true
            }
            else -> false
        }
    }

    private suspend fun medicamentoAindaValido(
        pacienteUid: String,
        medicamentoId: String,
        dataAgenda: String,
        horario: String
    ): Boolean {
        val documento = FirebaseFirestore.getInstance()
            .collection("medicamentos")
            .document(medicamentoId)
            .get(Source.SERVER)
            .await()
        if (!documento.exists()) return false
        val medicamento = Medicamento.fromFirestoreMap(medicamentoId, documento.data ?: emptyMap())
        if (medicamento.usuarioId != pacienteUid || !medicamento.ativo ||
            medicamento.horarios.none { it.hora == horario }
        ) return false
        val data = runCatching { java.time.LocalDate.parse(dataAgenda) }.getOrNull() ?: return false
        val inicio = if (medicamento.dataInicio.isBlank()) null
        else runCatching { java.time.LocalDate.parse(medicamento.dataInicio) }.getOrNull() ?: return false
        val fim = if (medicamento.dataFim.isBlank()) null
        else runCatching { java.time.LocalDate.parse(medicamento.dataFim) }.getOrNull() ?: return false
        if (inicio != null && data.isBefore(inicio)) return false
        if (fim != null && data.isAfter(fim)) return false
        return medicamento.frequencia != Medicamento.FREQUENCIA_PERSONALIZADA ||
            data.dayOfWeek.value in medicamento.diasSemana
    }

    private fun notificationId(deviceUid: String, doseId: String): Int = "$deviceUid|$doseId".hashCode()

    companion object {
        const val ACTION_DOSE_REMINDER = "br.com.tomai.action.DOSE_REMINDER"
        const val EXTRA_DEVICE_UID = "extra_device_uid"
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_PACIENTE_UID = "extra_paciente_uid"
        const val EXTRA_MEDICAMENTO_ID = "extra_medicamento_id"
        const val EXTRA_DATA_AGENDA = "extra_data_agenda"
        const val EXTRA_DOSE_SEM_DOCUMENTO = "extra_dose_sem_documento"
        const val EXTRA_MEDICAMENTO_NOME = "extra_medicamento_nome"
        const val EXTRA_DOSAGEM = "extra_dosagem"
        const val EXTRA_HORARIO = "extra_horario"
        private const val TAG = "TOMAI_NOTIFICACOES"
        private const val STATUS_PENDENTE = "PENDENTE"
    }
}
