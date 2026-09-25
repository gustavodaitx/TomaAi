package br.com.tomai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.AssinaturaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AssinaturaUiState(
    val planos: List<Map<String, Any?>> = emptyList(),
    val assinaturas: List<Map<String, Any?>> = emptyList(),
    val cobrancas: List<Map<String, Any?>> = emptyList(),
    val carregando: Boolean = false,
    val erro: String? = null,
    val mensagem: String? = null,
    val pixPayload: String? = null
)

class AssinaturaViewModel(private val repository: AssinaturaRepository = AssinaturaRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(AssinaturaUiState())
    val uiState = _uiState.asStateFlow()

    fun carregar(usuarioId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carregando = true, erro = null)
            runCatching { repository.listarPlanos() }
                .onSuccess { _uiState.value = _uiState.value.copy(planos = it, carregando = false) }
                .onFailure { falha("Não foi possível carregar os planos: ${it.localizedMessage}") }
        }
        repository.observarAssinaturas(usuarioId).onEach {
            _uiState.value = _uiState.value.copy(assinaturas = it)
        }.catch { falha("Não foi possível carregar as assinaturas.") }.launchIn(viewModelScope)
        repository.observarCobrancas(usuarioId).onEach {
            _uiState.value = _uiState.value.copy(cobrancas = it)
        }.catch { falha("Não foi possível carregar as cobranças.") }.launchIn(viewModelScope)
    }

    fun contratar(planoId: String, formaPagamento: String) = operacao("Solicitação enviada ao Asaas Sandbox.") {
        repository.criarAssinatura(planoId, formaPagamento)
    }

    fun atualizarCobrancas(assinaturaId: String) = operacao("Cobranças atualizadas.") {
        repository.consultarCobrancas(assinaturaId)
    }

    fun cancelar(assinaturaId: String) = operacao("Cancelamento solicitado.") {
        repository.cancelarAssinatura(assinaturaId)
    }

    fun obterPix(cobrancaId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carregando = true, erro = null, pixPayload = null)
            runCatching { repository.obterPix(cobrancaId) }
                .onSuccess { _uiState.value = _uiState.value.copy(carregando = false, pixPayload = it) }
                .onFailure { falha(it.localizedMessage ?: "Não foi possível obter o QR Pix.") }
        }
    }

    private fun operacao(sucesso: String, acao: suspend () -> Any) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carregando = true, erro = null, mensagem = null)
            runCatching { acao() }.onSuccess {
                _uiState.value = _uiState.value.copy(carregando = false, mensagem = sucesso)
            }.onFailure { falha(it.localizedMessage ?: "Falha na operação.") }
        }
    }

    private fun falha(mensagem: String) {
        _uiState.value = _uiState.value.copy(carregando = false, erro = mensagem)
    }
}
