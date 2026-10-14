package br.com.tomai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.AssinaturaRepository
import br.com.tomai.model.Plano
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AssinaturaUiState(
    val planos: List<Plano> = listOf(
        Plano("FREE", "Free", "Acesso integral a todas as funcionalidades", 0.0, "Sem cobrança", 0),
        Plano("QUINZENAL", "Quinzenal", "Apoie o desenvolvimento do TomaAí", 14.90, "A cada 15 dias", 15),
        Plano("MENSAL", "Mensal", "Apoie o desenvolvimento do TomaAí", 24.90, "A cada mês", 30)
    ),
    val carregando: Boolean = false,
    val erro: String? = null,
    val mensagem: String? = null
)

class AssinaturaViewModel(private val repository: AssinaturaRepository = AssinaturaRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(AssinaturaUiState())
    val uiState = _uiState.asStateFlow()

    fun carregar(@Suppress("UNUSED_PARAMETER") usuarioId: String) { }

    fun simularSelecao(usuarioId: String, planoId: String, metodo: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.atualizarPlanoUsuario(usuarioId, planoId, metodo) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        carregando = false,
                        mensagem = if (planoId == "FREE") "Plano Free selecionado." else "SIMULAçãO DE TESTE aprovada. Nenhum pagamento real foi realizado."
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(carregando = false, erro = "Não foi possível atualizar o plano. Verifique sua conexão e tente novamente.")
                }
        }
    }
}
