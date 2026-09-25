package br.com.tomai.viewmodel

import br.com.tomai.model.Dose

data class DoseUiState(
    val usuarioId: String = "",
    val dataAgenda: String = "",
    val doses: List<Dose> = emptyList(),
    val historico: List<Dose> = emptyList(),
    val carregandoHistorico: Boolean = false,
    val erroHistorico: String? = null,
    val isLoading: Boolean = false,
    val isSincronizandoAgenda: Boolean = false,
    val doseEmAtualizacao: String? = null,
    val erro: String? = null,
    val sucessoMensagem: String? = null
) {
    val pendentes: Int get() = doses.count { it.status == Dose.STATUS_PENDENTE }
    val concluidas: Int get() = doses.count {
        it.status != Dose.STATUS_PENDENTE
    }
}
