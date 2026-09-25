package br.com.tomai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.AuthRepository
import br.com.tomai.data.repository.AuthRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsável pela autenticação, cadastro, recuperação de senha e sessão do usuário.
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(estaAutenticado = authRepository.estaAutenticado())
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    init {
        observarUsuarioAtual()
    }

    private fun observarUsuarioAtual() {
        viewModelScope.launch {
            authRepository.usuarioAtualFlow.collect { usuario ->
                _uiState.update { estadoAtual ->
                    estadoAtual.copy(
                        usuario = usuario,
                        estaAutenticado = usuario != null
                    )
                }
            }
        }
    }

    fun login(email: String, senha: String, onSucesso: () -> Unit = {}) {
        val erroValidacao = validarCamposLogin(email, senha)
        if (erroValidacao != null) {
            _uiState.update { it.copy(erro = erroValidacao) }
            return
        }

        _uiState.update { it.copy(isLoading = true, erro = null, sucessoMensagem = null) }

        viewModelScope.launch {
            val resultado = authRepository.login(email = email, senha = senha)
            resultado.fold(
                onSuccess = { usuario ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            usuario = usuario,
                            estaAutenticado = true,
                            erro = null
                        )
                    }
                    onSucesso()
                },
                onFailure = { falha ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            erro = falha.message ?: "Falha ao realizar login."
                        )
                    }
                }
            )
        }
    }

    fun cadastrar(
        nome: String,
        email: String,
        senha: String,
        confirmacaoSenha: String,
        onSucesso: () -> Unit = {}
    ) {
        val erroValidacao = validarCamposCadastro(nome, email, senha, confirmacaoSenha)
        if (erroValidacao != null) {
            _uiState.update { it.copy(erro = erroValidacao) }
            return
        }

        _uiState.update { it.copy(isLoading = true, erro = null, sucessoMensagem = null) }

        viewModelScope.launch {
            val resultado = authRepository.cadastrar(nome = nome, email = email, senha = senha)
            resultado.fold(
                onSuccess = { usuario ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            usuario = usuario,
                            estaAutenticado = true,
                            erro = null,
                            sucessoMensagem = "Conta criada com sucesso!"
                        )
                    }
                    onSucesso()
                },
                onFailure = { falha ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            erro = falha.message ?: "Falha ao cadastrar usuário."
                        )
                    }
                }
            )
        }
    }

    fun recuperarSenha(email: String) {
        if (email.isBlank() || !emailRegex.matches(email.trim())) {
            _uiState.update { it.copy(erro = "Informe um endereço de e-mail válido.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, erro = null, sucessoMensagem = null) }

        viewModelScope.launch {
            val resultado = authRepository.recuperarSenha(email = email)
            resultado.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sucessoMensagem = "E-mail de recuperação enviado! Verifique sua caixa de entrada.",
                            erro = null
                        )
                    }
                },
                onFailure = { falha ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            erro = falha.message ?: "Falha ao solicitar recuperação de senha."
                        )
                    }
                }
            )
        }
    }

    fun logout(onConcluido: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update {
                AuthUiState(
                    isLoading = false,
                    usuario = null,
                    estaAutenticado = false
                )
            }
            onConcluido()
        }
    }

    fun limparMensagens() {
        _uiState.update { it.copy(erro = null, sucessoMensagem = null) }
    }

    private fun validarCamposLogin(email: String, senha: String): String? {
        if (email.isBlank()) {
            return "O e-mail é obrigatório."
        }
        if (!emailRegex.matches(email.trim())) {
            return "Digite um e-mail válido."
        }
        if (senha.isBlank()) {
            return "A senha é obrigatória."
        }
        return null
    }

    private fun validarCamposCadastro(
        nome: String,
        email: String,
        senha: String,
        confirmacaoSenha: String
    ): String? {
        if (nome.isBlank() || nome.trim().length < 2) {
            return "O nome deve conter pelo menos 2 caracteres."
        }
        if (email.isBlank()) {
            return "O e-mail é obrigatório."
        }
        if (!emailRegex.matches(email.trim())) {
            return "Digite um e-mail válido."
        }
        if (senha.length < 6) {
            return "A senha deve ter no mínimo 6 caracteres."
        }
        if (senha != confirmacaoSenha) {
            return "As senhas não coincidem."
        }
        return null
    }
}
