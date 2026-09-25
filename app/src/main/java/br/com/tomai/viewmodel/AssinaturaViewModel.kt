package br.com.tomai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.AssinaturaRepository
import br.com.tomai.data.repository.DadosCartaoContratacao
import br.com.tomai.model.Plano
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AssinaturaUiState(
    val planos: List<Plano> = emptyList(),
    val assinaturas: List<Map<String, Any?>> = emptyList(),
    val cobrancas: List<Map<String, Any?>> = emptyList(),
    val carregando: Boolean = false,
    val erro: String? = null,
    val mensagem: String? = null,
    val pixPayload: String? = null,
    val pixEncodedImage: String? = null
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

    fun contratarPix(planoId: String) = executarContratacao(planoId, "PIX", null)

    fun contratarCartao(planoId: String, cartao: DadosCartaoContratacao) =
        executarContratacao(planoId, "CREDIT_CARD", cartao)

    private fun executarContratacao(planoId: String, formaPagamento: String, cartao: DadosCartaoContratacao?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                carregando = true, erro = null, mensagem = null, pixPayload = null, pixEncodedImage = null
            )
            try {
                val result = repository.criarAssinatura(planoId, formaPagamento, cartao)
                val response = result.data as? Map<*, *> ?: emptyMap<Any, Any>()
                if (formaPagamento == "PIX") {
                    val pix = response["pix"] as? Map<*, *>
                    val payload = pix?.get("payload") as? String
                    if (payload.isNullOrBlank()) {
                        throw IllegalStateException(response["pixErro"] as? String
                            ?: "Assinatura criada, mas o Pix ainda não está disponível. Atualize as cobranças para tentar novamente.")
                    }
                    _uiState.value = _uiState.value.copy(
                        carregando = false,
                        mensagem = "Assinatura solicitada. Pague pelo Pix para ativá-la.",
                        pixPayload = payload,
                        pixEncodedImage = pix["encodedImage"] as? String
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        carregando = false,
                        mensagem = "Assinatura no cartão criada. A primeira cobrança vence na data informada."
                    )
                }
            } catch (erro: Exception) {
                falha(erro.localizedMessage ?: "Não foi possível contratar este plano.")
            }
        }
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
                .onSuccess { (payload, imagem) -> _uiState.value = _uiState.value.copy(carregando = false, pixPayload = payload, pixEncodedImage = imagem) }
                .onFailure { falha(it.localizedMessage ?: "Não foi possível obter o QR Pix.") }
        }
    }

    fun limparPixPayload() {
        _uiState.value = _uiState.value.copy(pixPayload = null, pixEncodedImage = null)
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
