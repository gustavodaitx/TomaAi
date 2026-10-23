package br.com.tomai.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import br.com.tomai.model.Dose
import br.com.tomai.model.Medicamento
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal const val INTERVALO_REPETICAO_LEMBRETE_MS = 2 * 60 * 1000L

/** Mantém os alarmes locais sincronizados com a agenda do usuário autenticado neste aparelho. */
object DoseReminderScheduler {
    private const val TAG = "TOMAI_NOTIFICACOES"
    private const val PREFS = "lembretes_medicamentos"
    private const val KEY_SCHEDULES = "agendamentos"
    private const val KEY_REPEAT_AT = "repetirEm"

    fun sincronizar(
        context: Context,
        uidDoDispositivo: String,
        doses: List<Dose>,
        medicamentos: List<Medicamento>
    ) {
        if (uidDoDispositivo.isBlank()) return
        val appContext = context.applicationContext
        doses.forEach { dose -> cancelarAgendamentoLegado(appContext, dose.id) }
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = readAll(prefs)
        val previous = all.optJSONArray(uidDoDispositivo) ?: JSONArray()
        val now = System.currentTimeMillis()
        val desired = LinkedHashMap<String, JSONObject>()
        val idsDeDosesPersistidas = doses.mapTo(mutableSetOf()) { it.id }

        val dosesParaAgendar = construirJanela(doses, medicamentos)
        dosesParaAgendar.asSequence()
            .filter { it.status == Dose.STATUS_PENDENTE && it.id.isNotBlank() }
            .forEach { dose ->
                val trigger = triggerMillis(dose) ?: return@forEach
                val prior = (0 until previous.length()).mapNotNull(previous::optJSONObject)
                    .firstOrNull { it.optString("doseId") == dose.id }
                val repeating = prior != null && prior.optLong(KEY_REPEAT_AT, 0L) > 0L
                if (trigger <= now && !repeating) return@forEach
                val item = JSONObject()
                    .put("doseId", dose.id)
                    .put("usuarioId", dose.usuarioId)
                    .put("medicamentoId", dose.medicamentoId)
                    .put("nome", dose.medicamentoNome)
                    .put("dosagem", dose.dosagem)
                    .put("horario", dose.horarioProgramado)
                    .put("data", dose.dataAgenda)
                    .put("doseSemDocumento", dose.id !in idsDeDosesPersistidas)
                if (repeating) item.put(KEY_REPEAT_AT, prior!!.optLong(KEY_REPEAT_AT))
                desired[dose.id] = item
            }

        for (index in 0 until previous.length()) {
            val old = previous.optJSONObject(index) ?: continue
            val id = old.optString("doseId")
            val new = desired[id]
            if (new == null || !sameSchedule(old, new)) {
                cancelar(appContext, uidDoDispositivo, id)
                DoseNotificationHelper.cancelarLembrete(appContext, notificationId(uidDoDispositivo, id))
            }
        }

        val next = JSONArray()
        desired.values.forEach { schedule ->
            val doseId = schedule.getString("doseId")
            val existing = (0 until previous.length())
                .mapNotNull(previous::optJSONObject)
                .firstOrNull { it.optString("doseId") == doseId && sameSchedule(it, schedule) }
            val toSchedule = existing ?: schedule
            val repetirEm = toSchedule.optLong(KEY_REPEAT_AT, 0L)
            if (repetirEm > 0L) agendar(appContext, uidDoDispositivo, toSchedule, repetirEm)
            else agendar(appContext, uidDoDispositivo, toSchedule)
            next.put(toSchedule)
        }
        all.put(uidDoDispositivo, next)
        prefs.edit().putString(KEY_SCHEDULES, all.toString()).apply()
        Log.d(TAG, "Agenda local sincronizada: ${desired.size} lembrete(s) para a conta ativa.")
    }

    /** Recria somente alarmes ainda futuros da conta salva neste aparelho. */
    fun restaurar(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = readAll(prefs)
        val now = System.currentTimeMillis()
        for (userIndex in 0 until all.length()) {
            val uid = all.names()?.optString(userIndex).orEmpty()
            if (uid.isBlank()) continue
            val old = all.optJSONArray(uid) ?: continue
            val valid = JSONArray()
            for (index in 0 until old.length()) {
                val schedule = old.optJSONObject(index) ?: continue
                val repeatAt = schedule.optLong(KEY_REPEAT_AT, 0L)
                val trigger = if (repeatAt > 0L) maxOf(repeatAt, now + INTERVALO_REPETICAO_LEMBRETE_MS) else triggerMillis(schedule) ?: continue
                if (repeatAt == 0L && trigger <= now) continue
                if (repeatAt > 0L) schedule.put(KEY_REPEAT_AT, trigger)
                agendar(appContext, uid, schedule, trigger)
                valid.put(schedule)
            }
            all.put(uid, valid)
        }
        prefs.edit().putString(KEY_SCHEDULES, all.toString()).apply()
        Log.d(TAG, "Alarmes locais futuros restaurados após evento do sistema.")
    }

    private fun agendar(context: Context, deviceUid: String, data: JSONObject, atMillis: Long? = null) {
        val trigger = atMillis ?: triggerMillis(data) ?: return
        if (trigger <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, deviceUid, data, PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
                Log.i(TAG, "Alarme aproximado agendado; acesso a alarmes exatos indisponível.")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            }
        } catch (error: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            Log.w(TAG, "Sem autorização para alarme exato; usado agendamento aproximado.", error)
        }
    }

    fun agendarRepeticao(
        context: Context,
        deviceUid: String,
        doseId: String,
        nome: String,
        dosagem: String,
        horario: String
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = readAll(prefs)
        val schedules = all.optJSONArray(deviceUid) ?: return
        val schedule = (0 until schedules.length()).mapNotNull(schedules::optJSONObject)
            .firstOrNull { it.optString("doseId") == doseId } ?: return
        val next = System.currentTimeMillis() + INTERVALO_REPETICAO_LEMBRETE_MS
        schedule.put(KEY_REPEAT_AT, next)
        schedule.put("nome", nome)
        schedule.put("dosagem", dosagem)
        schedule.put("horario", horario)
        prefs.edit().putString(KEY_SCHEDULES, all.toString()).apply()
        agendar(appContext, deviceUid, schedule, next)
    }

    fun cancelarRepeticao(context: Context, deviceUid: String, doseId: String) {
        val appContext = context.applicationContext
        cancelar(appContext, deviceUid, doseId)
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = readAll(prefs)
        val schedules = all.optJSONArray(deviceUid) ?: return
        val remaining = JSONArray()
        for (index in 0 until schedules.length()) {
            val schedule = schedules.optJSONObject(index) ?: continue
            if (schedule.optString("doseId") != doseId) remaining.put(schedule)
        }
        all.put(deviceUid, remaining)
        prefs.edit().putString(KEY_SCHEDULES, all.toString()).apply()
    }

    /** Cancela alarmes locais e notificações das doses removidas no servidor. */
    fun cancelarDoses(context: Context, deviceUid: String, doseIds: List<String>): Result<Unit> {
        if (deviceUid.isBlank() || doseIds.isEmpty()) return Result.success(Unit)
        return try {
            val appContext = context.applicationContext
            val ids = doseIds.toSet()
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val all = readAll(prefs)
            val schedules = all.optJSONArray(deviceUid) ?: JSONArray()
            val remaining = JSONArray()
            ids.forEach { doseId ->
                cancelar(appContext, deviceUid, doseId)
                cancelarAgendamentoLegado(appContext, doseId)
                DoseNotificationHelper.cancelarLembrete(appContext, notificationId(deviceUid, doseId))
                DoseNotificationHelper.cancelarLembrete(appContext, doseId.hashCode())
            }
            for (index in 0 until schedules.length()) {
                val schedule = schedules.optJSONObject(index) ?: continue
                if (schedule.optString("doseId") !in ids) remaining.put(schedule)
            }
            all.put(deviceUid, remaining)
            check(prefs.edit().putString(KEY_SCHEDULES, all.toString()).commit()) {
                "Não foi possível salvar a agenda local atualizada."
            }
            Log.i(TAG, "Lembretes locais cancelados para ${ids.size} dose(s) removida(s).")
            Result.success(Unit)
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao cancelar lembretes locais das doses removidas.", error)
            Result.failure(error)
        }
    }

    fun limparAgendamentos(context: Context, deviceUid: String) {
        if (deviceUid.isBlank()) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = readAll(prefs)
        val schedules = all.optJSONArray(deviceUid) ?: JSONArray()
        for (index in 0 until schedules.length()) {
            val schedule = schedules.optJSONObject(index) ?: continue
            val doseId = schedule.optString("doseId")
            cancelar(appContext, deviceUid, doseId)
            cancelarAgendamentoLegado(appContext, doseId)
            DoseNotificationHelper.cancelarLembrete(appContext, notificationId(deviceUid, doseId))
            DoseNotificationHelper.cancelarLembrete(appContext, doseId.hashCode())
        }
        all.put(deviceUid, JSONArray())
        prefs.edit().putString(KEY_SCHEDULES, all.toString()).apply()
    }

    private fun cancelar(context: Context, deviceUid: String, doseId: String) {
        if (doseId.isBlank()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val identity = Intent(context, DoseReminderReceiver::class.java).apply {
            action = DoseReminderReceiver.ACTION_DOSE_REMINDER
            data = uri(deviceUid, doseId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            identity,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Agendamento antigo cancelado.")
    }

    /** Limpa PendingIntents do agendador anterior, que não usava URI própria por conta. */
    private fun cancelarAgendamentoLegado(context: Context, doseId: String) {
        if (doseId.isBlank()) return
        val identity = Intent(context, DoseReminderReceiver::class.java)
        val oldPendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.hashCode(),
            identity,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(oldPendingIntent)
        oldPendingIntent.cancel()
        Log.d(TAG, "Agendamento legado removido.")
    }

    private fun pendingIntent(
        context: Context,
        deviceUid: String,
        data: JSONObject,
        flags: Int
    ): PendingIntent {
        val doseId = data.getString("doseId")
        val intent = Intent(context, DoseReminderReceiver::class.java).apply {
            action = DoseReminderReceiver.ACTION_DOSE_REMINDER
            this.data = uri(deviceUid, doseId)
            putExtra(DoseReminderReceiver.EXTRA_DEVICE_UID, deviceUid)
            putExtra(DoseReminderReceiver.EXTRA_DOSE_ID, doseId)
            putExtra(DoseReminderReceiver.EXTRA_PACIENTE_UID, data.optString("usuarioId"))
            putExtra(DoseReminderReceiver.EXTRA_MEDICAMENTO_ID, data.optString("medicamentoId"))
            putExtra(DoseReminderReceiver.EXTRA_DATA_AGENDA, data.optString("data"))
            putExtra(DoseReminderReceiver.EXTRA_DOSE_SEM_DOCUMENTO, data.optBoolean("doseSemDocumento"))
            putExtra(DoseReminderReceiver.EXTRA_MEDICAMENTO_NOME, data.optString("nome"))
            putExtra(DoseReminderReceiver.EXTRA_DOSAGEM, data.optString("dosagem"))
            putExtra(DoseReminderReceiver.EXTRA_HORARIO, data.optString("horario"))
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun uri(deviceUid: String, doseId: String): Uri =
        Uri.parse("tomai://lembrete/${Uri.encode(deviceUid)}/${Uri.encode(doseId)}")

    private fun notificationId(deviceUid: String, doseId: String): Int = "$deviceUid|$doseId".hashCode()

    private fun triggerMillis(data: JSONObject): Long? {
        val date = runCatching { LocalDate.parse(data.optString("data")) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(data.optString("horario")) }.getOrNull() ?: return null
        return LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun triggerMillis(dose: Dose): Long? = triggerMillis(
        JSONObject().put("data", dose.dataAgenda).put("horario", dose.horarioProgramado)
    )

    private fun construirJanela(doses: List<Dose>, medicamentos: List<Medicamento>): List<Dose> {
        val hoje = runCatching { LocalDate.parse(Dose.dataHoje()) }.getOrNull() ?: return emptyList()
        val dosesDeHoje = doses.associateBy { "${it.medicamentoId}|${it.horarioProgramado}" }
        val resultado = mutableListOf<Dose>()
        for (offset in 0..6) {
            val data = hoje.plusDays(offset.toLong())
            val dataIso = data.toString()
            medicamentos.filter { medicamentoValidoNaData(it, data) }.forEach { medicamento ->
                medicamento.horarios.forEach horarioLoop@ { horario ->
                    if (runCatching { LocalTime.parse(horario.hora) }.isFailure) return@horarioLoop
                    if (offset == 0) {
                        val doseAtual = dosesDeHoje["${medicamento.id}|${horario.hora}"]
                        if (doseAtual != null && doseAtual.status != Dose.STATUS_PENDENTE) return@horarioLoop
                        resultado += doseAtual ?: Dose(
                            id = Dose.idDeterministico(
                                medicamento.usuarioId,
                                dataIso,
                                medicamento.id,
                                horario.hora
                            ),
                            usuarioId = medicamento.usuarioId,
                            medicamentoId = medicamento.id,
                            medicamentoNome = medicamento.nome,
                            dosagem = medicamento.dosagem,
                            dataAgenda = dataIso,
                            horarioProgramado = horario.hora,
                            status = Dose.STATUS_PENDENTE
                        )
                    } else {
                        resultado += Dose(
                            id = Dose.idDeterministico(
                                medicamento.usuarioId,
                                dataIso,
                                medicamento.id,
                                horario.hora
                            ),
                            usuarioId = medicamento.usuarioId,
                            medicamentoId = medicamento.id,
                            medicamentoNome = medicamento.nome,
                            dosagem = medicamento.dosagem,
                            dataAgenda = dataIso,
                            horarioProgramado = horario.hora,
                            status = Dose.STATUS_PENDENTE
                        )
                    }
                }
            }
        }
        return resultado
    }

    private fun medicamentoValidoNaData(medicamento: Medicamento, data: LocalDate): Boolean {
        if (!medicamento.ativo || medicamento.id.isBlank() || medicamento.usuarioId.isBlank()) return false
        val inicio = if (medicamento.dataInicio.isBlank()) null
        else runCatching { LocalDate.parse(medicamento.dataInicio) }.getOrNull() ?: return false
        val fim = if (medicamento.dataFim.isBlank()) null
        else runCatching { LocalDate.parse(medicamento.dataFim) }.getOrNull() ?: return false
        if (inicio != null && data.isBefore(inicio)) return false
        if (fim != null && data.isAfter(fim)) return false
        return medicamento.frequencia != Medicamento.FREQUENCIA_PERSONALIZADA ||
            data.dayOfWeek.value in medicamento.diasSemana
    }

    private fun sameSchedule(first: JSONObject, second: JSONObject): Boolean =
        first.optString("data") == second.optString("data") &&
            first.optString("horario") == second.optString("horario") &&
            first.optString("medicamentoId") == second.optString("medicamentoId") &&
            first.optBoolean("doseSemDocumento") == second.optBoolean("doseSemDocumento") &&
            first.optString("nome") == second.optString("nome") &&
            first.optString("dosagem") == second.optString("dosagem")

    private fun readAll(prefs: android.content.SharedPreferences): JSONObject =
        runCatching { JSONObject(prefs.getString(KEY_SCHEDULES, "{}") ?: "{}") }
            .getOrElse { JSONObject() }
}
