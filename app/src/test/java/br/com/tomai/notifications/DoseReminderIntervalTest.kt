package br.com.tomai.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class DoseReminderIntervalTest {

    @Test
    fun repeticaoLocalUsaIntervaloDeDoisMinutos() {
        assertEquals(120_000L, INTERVALO_REPETICAO_LEMBRETE_MS)
    }
}
