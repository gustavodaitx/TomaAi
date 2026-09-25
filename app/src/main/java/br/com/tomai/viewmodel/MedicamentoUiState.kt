package br.com.tomai.viewmodel

import br.com.tomai.model.HorarioMedicamento
import br.com.tomai.model.Medicamento

data class MedicamentoUiState(
    val usuarioId: String = "",
    val medicamentos: List<Medicamento> = emptyList(),
    val isLoadingLista: Boolean = false,
    val isLoadingFormulario: Boolean = false,
    val isLoadingSalvar: Boolean = false,
    val medicamentoEmEdicao: Medicamento? = null,
    val nome: String = "",
    val dosagem: String = "",
    val frequencia: String = Medicamento.FREQUENCIA_DIARIA,
    val dataInicio: String = "",
    val dataFim: String = "",
    val diasSemana: Set<Int> = emptySet(),
    val horarios: List<HorarioMedicamento> = emptyList(),
    val estoqueInicial: String = "",
    val erro: String? = null,
    val sucessoMensagem: String? = null
)
