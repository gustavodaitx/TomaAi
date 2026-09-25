package br.com.tomai.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.PessoaConfiancaRepository
import br.com.tomai.model.PessoaConfianca
import kotlinx.coroutines.CancellationException
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

    fun salvar(usuarioId: String, nome: String, email: String, telefone: String, avisos: Boolean, pessoaId: String = "") {
        val emailNormalizado = email.trim()
        if (nome.isBlank() || emailNormalizado.isBlank()) {
            _uiState.value = _uiState.value.copy(erro = "Informe nome e e-mail.")
            return
        }
        if (!PessoaConfianca.emailValido(emailNormalizado)) {
            _uiState.value = _uiState.value.copy(erro = "Informe um e-mail válido.")
            return
        }
        executar(usuarioId) {
            repository.salvar(
                usuarioId,
                PessoaConfianca(
                    id = pessoaId,
                    nome = nome.trim(),
                    email = emailNormalizado,
                    telefone = telefone.trim(),
                    aceitouReceberAvisos = avisos
                )
            )
            _uiState.value = _uiState.value.copy(
                mensagem = if (pessoaId.isBlank()) "Pessoa de confiança cadastrada." else "Pessoa de confiança atualizada."
            )
        }
    }

    fun definirPadrao(usuarioId: String, pessoaId: String) = executar(usuarioId) {
        repository.definirPadrao(usuarioId, pessoaId)
        _uiState.value = _uiState.value.copy(mensagem = "Responsável padrão atualizado.")
    }

    fun remover(usuarioId: String, pessoaId: String) = executar(usuarioId) {
        repository.remover(usuarioId, pessoaId)
        _uiState.value = _uiState.value.copy(mensagem = "Pessoa removida.")
    }

    private fun executar(usuarioId: String, acao: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carregando = true, erro = null, mensagem = null)
            try {
                acao()
                _uiState.value = _uiState.value.copy(pessoas = repository.listar(usuarioId))
            } catch (cancelamento: CancellationException) {
                throw cancelamento
            } catch (erro: Exception) {
                Log.e("PessoaConfiancaVM", "Falha na operação de pessoa de confiança", erro)
                _uiState.value = _uiState.value.copy(erro = "Não foi possível salvar os dados. Tente novamente.")
            } finally {
                _uiState.value = _uiState.value.copy(carregando = false)
            }
        }
    }
}
