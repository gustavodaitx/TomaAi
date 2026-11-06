package br.com.tomai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DoseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getStringExtra(EXTRA_DOSE_ID).orEmpty()
        val deviceUid = intent.getStringExtra(EXTRA_DEVICE_UID).orEmpty()
        val nome = intent.getStringExtra(EXTRA_MEDICAMENTO_NOME).orEmpty()
        val dosagem = intent.getStringExtra(EXTRA_DOSAGEM).orEmpty()
        val horario = intent.getStringExtra(EXTRA_HORARIO).orEmpty()
        val patientUid = intent.getStringExtra(EXTRA_PATIENT_UID).orEmpty()
        Log.i(TAG, "receiver_entrada uidDispositivo=$deviceUid doseId=$doseId horario=$horario")
        if (doseId.isBlank() || deviceUid.isBlank()) {
            Log.w(TAG, "receiver_rejeitado motivo=dados_incompletos")
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
                val dose = snapshot?.takeIf { it.exists() }?.data
                if (snapshot?.exists() == true && (dose == null || dose["status"] != STATUS_PENDENTE)) {
                    Log.i(TAG, "receiver_rejeitado uidDispositivo=$deviceUid doseId=$doseId motivo=" +
                        if (dose == null) "dose_ausente" else "status_${dose["status"]}")
                    DoseReminderScheduler.cancelarRepeticao(context, deviceUid, doseId)
                    DoseNotificationHelper.cancelarLembrete(context, notificationId(deviceUid, doseId))
                    return@launch
                }
                if (snapshot != null) {
                    val autorizado = runCatching {
                        if (dose == null) destinatarioAindaAutorizado(deviceUid, patientUid)
                        else destinatarioAindaAutorizado(deviceUid, dose)
                    }
                        .onFailure { Log.w(TAG, "Não foi possível confirmar o vínculo no servidor.", it) }
                        .getOrNull()
                    if (autorizado == false) {
                        Log.i(TAG, "receiver_rejeitado uidDispositivo=$deviceUid doseId=$doseId motivo=vinculo_ou_consentimento")
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
                Log.i(TAG, "receiver_concluido uidDispositivo=$deviceUid doseId=$doseId " +
                    "estadoRemoto=${if (snapshot == null) "indisponivel" else "consultado"} repeticao=agendada")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun destinatarioAindaAutorizado(deviceUid: String, dose: Map<String, Any?>): Boolean {
        val patientUid = dose["usuarioId"] as? String ?: return false
        return destinatarioAindaAutorizado(deviceUid, patientUid)
    }

    private suspend fun destinatarioAindaAutorizado(deviceUid: String, patientUid: String): Boolean {
        if (patientUid.isBlank()) return false
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

    private fun notificationId(deviceUid: String, doseId: String): Int = "$deviceUid|$doseId".hashCode()

    companion object {
        const val ACTION_DOSE_REMINDER = "br.com.tomai.action.DOSE_REMINDER"
        const val EXTRA_DEVICE_UID = "extra_device_uid"
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_MEDICAMENTO_NOME = "extra_medicamento_nome"
        const val EXTRA_DOSAGEM = "extra_dosagem"
        const val EXTRA_HORARIO = "extra_horario"
        const val EXTRA_PATIENT_UID = "extra_patient_uid"
        private const val TAG = "TOMAI_NOTIFICACOES"
        private const val STATUS_PENDENTE = "PENDENTE"
    }
}
