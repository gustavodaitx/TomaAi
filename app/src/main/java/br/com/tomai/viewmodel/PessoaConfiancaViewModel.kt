package br.com.tomai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.PessoaConfiancaRepository
import br.com.tomai.model.PessoaConfianca
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PessoaConfiancaUiState(
    val pessoas: List<PessoaConfianca> = emptyList(),
    val carregando: Boolean = false,
    val erro: String? = null,
    val mensagem: String? = null
)

class PessoaConfiancaViewModel(
    private val repository: PessoaConfiancaRepository = PessoaConfiancaRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(PessoaConfiancaUiState())
    val uiState = _uiState.asStateFlow()

    fun carregar(usuarioId: String) = executar(usuarioId) { }

    fun salvar(usuarioId: String, nome: String, email: String, telefone: String, avisos: Boolean) {
        if (nome.isBlank() || email.isBlank()) {
            _uiState.value = _uiState.value.copy(erro = "Informe nome e e-mail.")
            return
        }
        executar(usuarioId) {
            repository.salvar(usuarioId, PessoaConfianca(nome = nome.trim(), email = email.trim(), telefone = telefone.trim(), aceitouReceberAvisos = avisos))
            _uiState.value = _uiState.value.copy(mensagem = "Pessoa de confiança cadastrada.")
        }
    }

    fun definirPadrao(usuarioId: String, pessoaId: String) = executar(usuarioId) {
        repository.definirPadrao(usuarioId, pessoaId)
        _uiState.value = _uiState.value.copy(mensagem = "Responsável padrão atualizado.")
    }

    fun remover(usuarioId: String, pessoaId: String) = executar(usuarioId) {
        repository.remover(usuarioId, pessoaId)
    }

    private fun executar(usuarioId: String, acao: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carregando = true, erro = null, mensagem = null)
            runCatching { acao(); _uiState.value = _uiState.value.copy(pessoas = repository.listar(usuarioId)) }
                .onFailure { _uiState.value = _uiState.value.copy(erro = it.localizedMessage ?: "Não foi possível salvar os dados.") }
            _uiState.value = _uiState.value.copy(carregando = false)
        }
    }
}
