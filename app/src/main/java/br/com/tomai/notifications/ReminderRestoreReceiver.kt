package br.com.tomai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("TOMAI_NOTIFICACOES", "Evento do sistema recebido para recalcular lembretes.")
        DoseReminderScheduler.restaurar(context)
    }
}
