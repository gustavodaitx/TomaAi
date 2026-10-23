package br.com.tomai.data.repository

import br.com.tomai.model.Medicamento
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicamentosValidosNaDataTest {

    @Test
    fun medicamentoDoPacienteVinculadoPodeSerAgendadoNaDataSolicitada() {
        val medicamento = Medicamento(
            id = "med-1",
            usuarioId = "paciente-1",
            ativo = true
        )

        assertTrue(medicamento.ehValidoNaData("paciente-1", "2026-10-22"))
    }

    @Test
    fun rejeitaMedicamentoDeOutroPacienteOuInativo() {
        val medicamento = Medicamento(
            id = "med-1",
            usuarioId = "paciente-1",
            ativo = true
        )
        val inativo = medicamento.copy(ativo = false)

        assertFalse(medicamento.ehValidoNaData("paciente-2", "2026-10-22"))
        assertFalse(inativo.ehValidoNaData("paciente-1", "2026-10-22"))
    }

    @Test
    fun respeitaPeriodoERecorrenciaPersonalizada() {
        val medicamento = Medicamento(
            id = "med-1",
            usuarioId = "paciente-1",
            frequencia = Medicamento.FREQUENCIA_PERSONALIZADA,
            dataInicio = "2026-10-20",
            dataFim = "2026-10-30",
            diasSemana = listOf(4)
        )

        assertTrue(medicamento.ehValidoNaData("paciente-1", "2026-10-22"))
        assertFalse(medicamento.ehValidoNaData("paciente-1", "2026-10-23"))
        assertFalse(medicamento.ehValidoNaData("paciente-1", "2026-11-01"))
    }
}
