package br.com.tomai.data.repository

import br.com.tomai.model.Medicamento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseReminderSelectionTest {
    @Test
    fun `seleciona medicamentos ativos validos para a data dos lembretes vinculados`() {
        val medicamentos = listOf(
            Medicamento(id = "diario"),
            Medicamento(
                id = "segunda",
                frequencia = Medicamento.FREQUENCIA_PERSONALIZADA,
                diasSemana = listOf(1)
            ),
            Medicamento(
                id = "terca",
                frequencia = Medicamento.FREQUENCIA_PERSONALIZADA,
                diasSemana = listOf(2)
            ),
            Medicamento(id = "inativo", ativo = false),
            Medicamento(id = "ainda-nao-iniciado", dataInicio = "2025-01-07"),
            Medicamento(id = "encerrado", dataFim = "2025-01-05")
        )

        val selecionados = selecionarMedicamentosValidosNaData(medicamentos, "2025-01-06")

        assertEquals(setOf("diario", "segunda"), selecionados.keys)
    }

    @Test
    fun `data invalida nao seleciona medicamentos`() {
        val selecionados = selecionarMedicamentosValidosNaData(
            listOf(Medicamento(id = "diario")),
            "data-invalida"
        )

        assertTrue(selecionados.isEmpty())
    }
}
