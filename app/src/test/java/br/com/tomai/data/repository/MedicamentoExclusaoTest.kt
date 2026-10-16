package br.com.tomai.data.repository

import br.com.tomai.model.Dose
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MedicamentoExclusaoTest {
    private val agora = Instant.parse("2025-01-01T11:00:00Z") // 08:00 em São Paulo

    @Test
    fun `dose pendente futura deve ser removida`() {
        assertTrue(doseEhPendenteFutura(dose("2025-01-01", "08:01"), agora))
    }

    @Test
    fun `dose confirmada futura deve ser preservada`() {
        assertFalse(doseEhPendenteFutura(dose("2025-01-01", "09:00", Dose.STATUS_CONFIRMADA), agora))
    }

    @Test
    fun `dose ignorada ou marcada como nao confirmada deve ser preservada`() {
        assertFalse(doseEhPendenteFutura(dose("2025-01-01", "09:00", Dose.STATUS_IGNORADA), agora))
        assertFalse(doseEhPendenteFutura(dose("2025-01-01", "09:00", Dose.STATUS_NAO_CONFIRMADA), agora))
    }

    @Test
    fun `dose pendente passada deve ser preservada`() {
        assertFalse(doseEhPendenteFutura(dose("2025-01-01", "07:59"), agora))
    }

    @Test
    fun `data ou horario invalido deve ser preservado`() {
        assertFalse(doseEhPendenteFutura(dose("inválida", "09:00"), agora))
    }

    @Test
    fun `medicamento em exclusao nao pode gerar doses`() {
        assertTrue(medicineDocumentBlocksDoseGeneration(mapOf("exclusaoEmAndamento" to true)))
        assertFalse(medicineDocumentBlocksDoseGeneration(mapOf("exclusaoEmAndamento" to false)))
        assertFalse(medicineDocumentBlocksDoseGeneration(emptyMap()))
    }

    private fun dose(data: String, horario: String, status: String = Dose.STATUS_PENDENTE) =
        Dose(dataAgenda = data, horarioProgramado = horario, status = status)
}
