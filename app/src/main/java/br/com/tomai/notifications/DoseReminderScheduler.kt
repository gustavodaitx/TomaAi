package br.com.tomai.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import br.com.tomai.model.Dose
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object DoseReminderScheduler {

    fun reagendarPendentes(context: Context, doses: List<Dose>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        doses.filter { it.status == Dose.STATUS_PENDENTE }.forEach { dose ->
            val triggerAt = calcularTriggerMillis(dose) ?: return@forEach
            if (triggerAt <= System.currentTimeMillis()) return@forEach

            val intent = Intent(context, DoseReminderReceiver::class.java).apply {
                putExtra(DoseReminderReceiver.EXTRA_DOSE_ID, dose.id)
                putExtra(DoseReminderReceiver.EXTRA_MEDICAMENTO_NOME, dose.medicamentoNome)
                putExtra(DoseReminderReceiver.EXTRA_HORARIO, dose.horarioProgramado)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                dose.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        }
    }

    private fun calcularTriggerMillis(dose: Dose): Long? {
        val data = runCatching { LocalDate.parse(dose.dataAgenda) }.getOrNull() ?: return null
        val hora = runCatching { LocalTime.parse(dose.horarioProgramado) }.getOrNull() ?: return null
        val dateTime = LocalDateTime.of(data, hora)
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
