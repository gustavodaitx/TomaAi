package br.com.tomai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DoseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getStringExtra(EXTRA_DOSE_ID).orEmpty()
        val deviceUid = intent.getStringExtra(EXTRA_DEVICE_UID).orEmpty()
        val nome = intent.getStringExtra(EXTRA_MEDICAMENTO_NOME).orEmpty()
        val dosagem = intent.getStringExtra(EXTRA_DOSAGEM).orEmpty()
        val horario = intent.getStringExtra(EXTRA_HORARIO).orEmpty()
        if (doseId.isBlank() || deviceUid.isBlank()) {
            Log.w("TOMAI_NOTIFICACOES", "Evento de lembrete ignorado por dados incompletos.")
            return
        }
        val notificationId = doseId.hashCode()

        DoseNotificationHelper.exibirLembrete(
            context = context,
            notificationId = notificationId,
            titulo = "Hora do medicamento",
            mensagem = if (nome.isNotBlank()) {
                listOf(nome, dosagem, horario).filter(String::isNotBlank).joinToString(" · ")
            } else {
                "Confira sua dose programada para $horario."
            }
        )
    }

    companion object {
        const val ACTION_DOSE_REMINDER = "br.com.tomai.action.DOSE_REMINDER"
        const val EXTRA_DEVICE_UID = "extra_device_uid"
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_MEDICAMENTO_NOME = "extra_medicamento_nome"
        const val EXTRA_DOSAGEM = "extra_dosagem"
        const val EXTRA_HORARIO = "extra_horario"
    }
}
