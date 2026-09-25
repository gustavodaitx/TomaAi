package br.com.tomai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DoseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getStringExtra(EXTRA_DOSE_ID).orEmpty()
        val nome = intent.getStringExtra(EXTRA_MEDICAMENTO_NOME).orEmpty()
        val horario = intent.getStringExtra(EXTRA_HORARIO).orEmpty()
        val notificationId = doseId.hashCode()

        DoseNotificationHelper.exibirLembrete(
            context = context,
            notificationId = notificationId,
            titulo = "Hora do medicamento",
            mensagem = if (nome.isNotBlank()) {
                "$nome — $horario"
            } else {
                "Confira sua dose programada para $horario."
            }
        )
    }

    companion object {
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_MEDICAMENTO_NOME = "extra_medicamento_nome"
        const val EXTRA_HORARIO = "extra_horario"
    }
}
