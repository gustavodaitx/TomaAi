package br.com.tomai.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.AuthRepository
import br.com.tomai.data.repository.AuthRepositoryImpl
import br.com.tomai.model.Usuario
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsável pela autenticação, cadastro, recuperação de senha e sessão do usuário.
 * Sincroniza em tempo real com o Cloud Firestore.
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    companion object {
        private const val TAG = "TomaAi_AuthViewModel"
    }

    private var perfilObservationJob: Job? = null

    private val _uiState = MutableStateFlow(
        AuthUiState(
            estaAutenticado = authRepository.estaAutenticado(),
            firestoreUid = authRepository.obterUsuarioAtualId(),
            usuario = authRepository.obterUsuarioAtualId()?.let { uid ->
                Usuario(id = uid, perfil = Usuario.PERFIL_PACIENTE)
            }
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    init {
        observarUsuarioAtual()
    }

    fun obterUidAutenticado(): String? {
        val uid = authRepository.obterUsuarioAtualId()
        Log.d(TAG, "obterUidAutenticado: $uid")
        return uid
    }

    /**
     * Inicia a observação reativa do documento /usuarios/{uid} em tempo real via Firestore listener.
     */
    fun observarUsuarioFirestore(uid: String) {
        if (uid.isBlank()) {
            Log.e(TAG, "observarUsuarioFirestore: UID informado está em branco.")
            return
        }

        Log.d(TAG, "observarUsuarioFirestore: Conectando listener em tempo real para UID: $uid")
        perfilObservationJob?.cancel()
        perfilObservationJob = viewModelScope.launch {
            authRepository.observarPerfil(uid).collect { usuarioAtualizado ->
                Log.d(TAG, "observarUsuarioFirestore: Dados recebidos do Firestore para UID: $uid: $usuarioAtualizado")
                val uidFinal = usuarioAtualizado?.id?.takeIf { it.isNotBlank() } ?: uid
                _uiState.update { estado ->
                    estado.copy(
                        usuario = usuarioAtualizado ?: estado.usuario,
                        firestoreUid = uidFinal,
                        estaAutenticado = true
                    )
                }
            }
        }
    }

    private fun observarUsuarioAtual() {
        viewModelScope.launch {
            authRepository.usuarioAtualFlow.collect { usuario ->
                Log.d(TAG, "observarUsuarioAtual: usuarioAtualFlow emitiu: $usuario")
                val uid = usuario?.id?.takeIf { it.isNotBlank() } ?: authRepository.obterUsuarioAtualId()
                _uiState.update { estadoAtual ->
                    estadoAtual.copy(
                        usuario = usuario ?: if (authRepository.estaAutenticado()) {
                            uid?.let { Usuario(id = it, perfil = Usuario.PERFIL_PACIENTE) }
                        } else null,
                        firestoreUid = uid,
                        estaAutenticado = usuario != null || authRepository.estaAutenticado()
                    )
                }

                if (!uid.isNullOrBlank() && perfilObservationJob == null) {
                    observarUsuarioFirestore(uid)
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
            Log.d(TAG, "login: Disparando autenticação para $email")
            val resultado = authRepository.login(email = email, senha = senha)
            resultado.fold(
                onSuccess = { usuario ->
                    Log.d(TAG, "login: Sucesso para UID: ${usuario.id}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            usuario = usuario,
                            firestoreUid = usuario.id,
                            estaAutenticado = true,
                            erro = null
                        )
                    }
                    observarUsuarioFirestore(usuario.id)
                    onSucesso()
                },
                onFailure = { falha ->
                    Log.e(TAG, "login: Falha ao autenticar: ${falha.message}")
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
            Log.d(TAG, "cadastrar: Disparando cadastro para $email")
            val resultado = authRepository.cadastrar(nome = nome, email = email, senha = senha)
            resultado.fold(
                onSuccess = { usuario ->
                    Log.d(TAG, "cadastrar: Sucesso para UID: ${usuario.id}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            usuario = usuario,
                            firestoreUid = usuario.id,
                            estaAutenticado = true,
                            erro = null,
                            sucessoMensagem = "Conta criada com sucesso!"
                        )
                    }
                    observarUsuarioFirestore(usuario.id)
                    onSucesso()
                },
                onFailure = { falha ->
                    Log.e(TAG, "cadastrar: Falha ao cadastrar: ${falha.message}")
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
            Log.d(TAG, "logout: Finalizando sessão e cancelando listeners")
            perfilObservationJob?.cancel()
            perfilObservationJob = null
            authRepository.logout()
            _uiState.update {
                AuthUiState(
                    isLoading = false,
                    usuario = null,
                    estaAutenticado = false,
                    firestoreUid = null
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
