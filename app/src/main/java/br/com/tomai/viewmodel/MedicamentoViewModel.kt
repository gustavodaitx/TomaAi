package br.com.tomai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.MedicamentoRepository
import br.com.tomai.data.repository.MedicamentoRepositoryImpl
import br.com.tomai.model.HorarioMedicamento
import br.com.tomai.model.Medicamento
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MedicamentoViewModel(
    private val repository: MedicamentoRepository = MedicamentoRepositoryImpl()
) : ViewModel() {

    private var observacaoJob: Job? = null

    private val _uiState = MutableStateFlow(MedicamentoUiState())
    val uiState: StateFlow<MedicamentoUiState> = _uiState.asStateFlow()

    private val horaRegex = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")

    fun iniciarObservacao(usuarioId: String) {
        if (usuarioId.isBlank()) return
        if (_uiState.value.usuarioId == usuarioId && observacaoJob?.isActive == true) return

        observacaoJob?.cancel()
        _uiState.update { it.copy(usuarioId = usuarioId, isLoadingLista = true, erro = null) }

        observacaoJob = viewModelScope.launch {
            repository.observarMedicamentos(usuarioId).collect { lista ->
                _uiState.update {
                    it.copy(
                        medicamentos = lista,
                        isLoadingLista = false
                    )
                }
            }
        }
    }

    fun prepararNovoMedicamento() {
        _uiState.update {
            it.copy(
                medicamentoEmEdicao = null,
                nome = "",
                dosagem = "",
                frequencia = Medicamento.FREQUENCIA_DIARIA,
                horarios = emptyList(),
                estoqueInicial = "",
                erro = null,
                sucessoMensagem = null
            )
        }
    }

    fun carregarParaEdicao(medicamentoId: String) {
        if (medicamentoId.isBlank() || medicamentoId == ScreenMedicamentoId.NOVO) {
            prepararNovoMedicamento()
            return
        }

        _uiState.update { it.copy(isLoadingFormulario = true, erro = null) }
        viewModelScope.launch {
            repository.buscarPorId(medicamentoId).fold(
                onSuccess = { medicamento ->
                    if (medicamento == null) {
                        _uiState.update {
                            it.copy(
                                isLoadingFormulario = false,
                                erro = "Medicamento não encontrado."
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                medicamentoEmEdicao = medicamento,
                                nome = medicamento.nome,
                                dosagem = medicamento.dosagem,
                                frequencia = medicamento.frequencia,
                                horarios = medicamento.horarios,
                                estoqueInicial = medicamento.estoqueInicial.toString(),
                                isLoadingFormulario = false
                            )
                        }
                    }
                },
                onFailure = { falha ->
                    _uiState.update {
                        it.copy(
                            isLoadingFormulario = false,
                            erro = falha.message
                        )
                    }
                }
            )
        }
    }

    fun atualizarNome(valor: String) {
        _uiState.update { it.copy(nome = valor, erro = null) }
    }

    fun atualizarDosagem(valor: String) {
        _uiState.update { it.copy(dosagem = valor, erro = null) }
    }

    fun atualizarFrequencia(valor: String) {
        _uiState.update { it.copy(frequencia = valor, erro = null) }
    }

    fun atualizarEstoqueInicial(valor: String) {
        val filtrado = valor.filter { it.isDigit() }
        _uiState.update { it.copy(estoqueInicial = filtrado, erro = null) }
    }

    fun adicionarHorario(hora: String) {
        val horaNormalizada = normalizarHora(hora)
        if (!horaRegex.matches(horaNormalizada)) {
            _uiState.update { it.copy(erro = "Informe um horário válido (HH:mm).") }
            return
        }
        val duplicado = _uiState.value.horarios.any { it.hora == horaNormalizada }
        if (duplicado) {
            _uiState.update { it.copy(erro = "Este horário já foi adicionado.") }
            return
        }
        _uiState.update {
            it.copy(
                horarios = it.horarios + HorarioMedicamento(hora = horaNormalizada),
                erro = null
            )
        }
    }

    fun removerHorario(hora: String) {
        _uiState.update {
            it.copy(horarios = it.horarios.filterNot { h -> h.hora == hora })
        }
    }

    fun salvar(onSucesso: () -> Unit = {}) {
        val estado = _uiState.value
        val erroValidacao = validarFormulario(estado)
        if (erroValidacao != null) {
            _uiState.update { it.copy(erro = erroValidacao) }
            return
        }

        val estoque = estado.estoqueInicial.toIntOrNull() ?: 0
        val edicao = estado.medicamentoEmEdicao
        val medicamento = Medicamento(
            id = edicao?.id ?: "",
            usuarioId = estado.usuarioId,
            nome = estado.nome.trim(),
            dosagem = estado.dosagem.trim(),
            frequencia = estado.frequencia,
            horarios = estado.horarios.sortedBy { it.hora },
            estoqueInicial = if (edicao != null) edicao.estoqueInicial else estoque,
            estoqueAtual = edicao?.estoqueAtual ?: estoque,
            ativo = true
        )

        _uiState.update { it.copy(isLoadingSalvar = true, erro = null) }
        viewModelScope.launch {
            repository.salvar(medicamento).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoadingSalvar = false,
                            sucessoMensagem = "Medicamento salvo com sucesso."
                        )
                    }
                    onSucesso()
                },
                onFailure = { falha ->
                    _uiState.update {
                        it.copy(
                            isLoadingSalvar = false,
                            erro = falha.message
                        )
                    }
                }
            )
        }
    }

    fun desativar(medicamentoId: String, onSucesso: () -> Unit = {}) {
        _uiState.update { it.copy(isLoadingLista = true, erro = null) }
        viewModelScope.launch {
            repository.desativar(medicamentoId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoadingLista = false,
                            sucessoMensagem = "Medicamento removido."
                        )
                    }
                    onSucesso()
                },
                onFailure = { falha ->
                    _uiState.update {
                        it.copy(
                            isLoadingLista = false,
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

    private fun validarFormulario(estado: MedicamentoUiState): String? {
        if (estado.usuarioId.isBlank()) {
            return "Sessão inválida. Faça login novamente."
        }
        if (estado.nome.trim().length < 2) {
            return "Informe o nome do medicamento (mín. 2 caracteres)."
        }
        if (estado.dosagem.isBlank()) {
            return "Informe a dosagem (ex: 500mg, 1 comprimido)."
        }
        if (estado.horarios.isEmpty()) {
            return "Adicione pelo menos um horário."
        }
        return null
    }

    private fun normalizarHora(hora: String): String {
        val partes = hora.trim().split(":")
        if (partes.size != 2) return hora.trim()
        val h = partes[0].padStart(2, '0')
        val m = partes[1].padStart(2, '0')
        return "$h:$m"
    }
}

object ScreenMedicamentoId {
    const val NOVO = "novo"
}
