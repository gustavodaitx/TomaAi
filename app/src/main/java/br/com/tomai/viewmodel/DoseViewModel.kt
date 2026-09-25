package br.com.tomai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.DoseRepository
import br.com.tomai.data.repository.DoseRepositoryImpl
import br.com.tomai.data.repository.MedicamentoRepository
import br.com.tomai.data.repository.MedicamentoRepositoryImpl
import br.com.tomai.model.Dose
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DoseViewModel(
    private val doseRepository: DoseRepository = DoseRepositoryImpl(),
    private val medicamentoRepository: MedicamentoRepository = MedicamentoRepositoryImpl()
) : ViewModel() {

    private var dosesJob: Job? = null
    private var medicamentosJob: Job? = null

    private val _uiState = MutableStateFlow(DoseUiState())
    val uiState: StateFlow<DoseUiState> = _uiState.asStateFlow()

    fun carregarHistorico(usuarioId: String, dataInicio: String, dataFim: String) {
        _uiState.update { it.copy(carregandoHistorico = true, erroHistorico = null) }
        viewModelScope.launch {
            runCatching { doseRepository.buscarHistorico(usuarioId, dataInicio, dataFim) }
                .onSuccess { doses -> _uiState.update { it.copy(historico = doses, carregandoHistorico = false) } }
                .onFailure { erro -> _uiState.update {
                    it.copy(carregandoHistorico = false, erroHistorico = erro.localizedMessage ?: "Não foi possível carregar o histórico.")
                } }
        }
    }

    fun iniciar(usuarioId: String) {
        if (usuarioId.isBlank()) return
        val dataHoje = Dose.dataHoje()
        if (_uiState.value.usuarioId == usuarioId &&
            _uiState.value.dataAgenda == dataHoje &&
            dosesJob?.isActive == true
        ) {
            return
        }

        dosesJob?.cancel()
        medicamentosJob?.cancel()

        _uiState.update {
            it.copy(
                usuarioId = usuarioId,
                dataAgenda = dataHoje,
                isLoading = true,
                erro = null
            )
        }

        medicamentosJob = viewModelScope.launch {
            try {
                medicamentoRepository.observarMedicamentos(usuarioId).collect { medicamentos ->
                _uiState.update { it.copy(isSincronizandoAgenda = true) }
                try {
                    doseRepository.garantirAgendaDiaria(
                        usuarioId = usuarioId,
                        medicamentos = medicamentos,
                        dataAgenda = dataHoje
                    ).onFailure { falha ->
                        _uiState.update { it.copy(erro = falha.message) }
                    }
                } catch (cancelamento: CancellationException) {
                    throw cancelamento
                } catch (falha: Exception) {
                    _uiState.update { it.copy(erro = falha.localizedMessage ?: "Não foi possível sincronizar a agenda.") }
                } finally {
                    _uiState.update { it.copy(isSincronizandoAgenda = false) }
                }
            }
            } catch (cancelamento: CancellationException) {
                throw cancelamento
            } catch (falha: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isSincronizandoAgenda = false,
                        erro = falha.localizedMessage ?: "Não foi possível carregar os medicamentos.")
                }
            }
        }

        dosesJob = viewModelScope.launch {
            try {
                doseRepository.observarDosesDoDia(usuarioId, dataHoje).collect { doses ->
                _uiState.update {
                    it.copy(
                        doses = doses,
                        isLoading = false
                    )
                }
            }
            } catch (cancelamento: CancellationException) {
                throw cancelamento
            } catch (falha: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, erro = falha.localizedMessage ?: "Não foi possível carregar as doses.")
                }
            }
        }
    }

    fun marcarTomado(doseId: String, medicamentoId: String) {
        atualizar(doseId, Dose.STATUS_CONFIRMADA, medicamentoId, "Dose confirmada.")
    }

    fun marcarPular(doseId: String, medicamentoId: String) {
        atualizar(doseId, Dose.STATUS_IGNORADA, medicamentoId, "Dose ignorada.")
    }

    fun marcarAtrasado(doseId: String, medicamentoId: String) {
        atualizar(doseId, Dose.STATUS_NAO_CONFIRMADA, medicamentoId, "Marcada como não confirmada.")
    }

    private fun atualizar(
        doseId: String,
        status: String,
        medicamentoId: String,
        mensagemSucesso: String
    ) {
        _uiState.update {
            it.copy(doseEmAtualizacao = doseId, erro = null, sucessoMensagem = null)
        }
        viewModelScope.launch {
            doseRepository.atualizarStatus(doseId, status, medicamentoId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            doseEmAtualizacao = null,
                            sucessoMensagem = mensagemSucesso
                        )
                    }
                },
                onFailure = { falha ->
                    _uiState.update {
                        it.copy(
                            doseEmAtualizacao = null,
                            erro = falha.message
                        )
                    }
                }
            )
        }
    }

    fun limparMensagens() {
        _uiState.update { it.copy(erro = null, sucessoMensagem = null) }
    }
}
