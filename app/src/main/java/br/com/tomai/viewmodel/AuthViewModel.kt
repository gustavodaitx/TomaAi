package br.com.tomai.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tomai.data.repository.AuthRepository
import br.com.tomai.data.repository.AuthRepositoryException
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
    private val codigosPacienteVerificados = mutableSetOf<String>()

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
                val uidFinal = usuarioAtualizado?.id?.takeIf { it.isNotBlank() } ?: uid
                val usuarioAnterior = _uiState.value.usuario
                val usuarioVisivel = usuarioAtualizado?.let { recebido ->
                    val anterior = usuarioAnterior?.takeIf { it.id == recebido.id }
                    if (anterior != null) {
                        recebido.copy(
                            nome = recebido.nome.ifBlank { anterior.nome },
                            telefone = recebido.telefone.ifBlank { anterior.telefone },
                            email = recebido.email.ifBlank { anterior.email },
                            codigoPaciente = recebido.codigoPaciente?.takeIf { it.isNotBlank() }
                                ?: recebido.codigoVinculo?.takeIf { it.isNotBlank() }
                                ?: anterior.codigoPaciente
                                ?: anterior.codigoVinculo,
                            codigoVinculo = recebido.codigoVinculo?.takeIf { it.isNotBlank() }
                                ?: recebido.codigoPaciente?.takeIf { it.isNotBlank() }
                                ?: anterior.codigoVinculo
                                ?: anterior.codigoPaciente
                        )
                    } else recebido
                }
                Log.d(TAG, "observarUsuarioFirestore: Dados recebidos do Firestore para UID: $uid: $usuarioVisivel")
                _uiState.update { estado ->
                    estado.copy(
                        usuario = usuarioVisivel ?: estado.usuario,
                        firestoreUid = uidFinal,
                        estaAutenticado = true
                    )
                }
                if (usuarioVisivel?.perfil == Usuario.PERFIL_PACIENTE &&
                    usuarioVisivel.codigoPaciente.isNullOrBlank() &&
                    usuarioVisivel.codigoVinculo.isNullOrBlank() &&
                    !_uiState.value.isLoading &&
                    codigosPacienteVerificados.add(uidFinal)) {
                    viewModelScope.launch {
                        authRepository.garantirCodigoPaciente(uidFinal).fold(
                            onSuccess = { codigo ->
                                _uiState.update { estado ->
                                    val usuarioAtual = estado.usuario
                                    if (usuarioAtual?.id == uidFinal && usuarioAtual.perfil == Usuario.PERFIL_PACIENTE) {
                                        estado.copy(usuario = usuarioAtual.copy(codigoPaciente = codigo, codigoVinculo = codigo))
                                    } else estado
                                }
                                Log.d(TAG, "observarUsuarioFirestore: Código de vínculo reparado para UID $uidFinal: $codigo")
                            },
                            onFailure = { falha ->
                                Log.w(TAG, "observarUsuarioFirestore: Não foi possível reparar o código do paciente para UID $uidFinal: ${falha.message}")
                            }
                        )
                    }
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
                    codigosPacienteVerificados.remove(usuario.id)
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
                            erro = mensagemFalhaSegura(falha, "Falha ao realizar login.")
                        )
                    }
                }
            )
        }
    }

    fun cadastrar(
        nome: String,
        telefone: String,
        email: String,
        senha: String,
        confirmacaoSenha: String,
        codigoPaciente: String? = null,
        cadastroPessoaDeConfianca: Boolean = false,
        perfilCadastro: String? = null,
        onSucesso: () -> Unit = {}
    ) {
        if (_uiState.value.isLoading) return

        val perfilSolicitado = perfilCadastro?.trim()?.uppercase()
            ?: if (cadastroPessoaDeConfianca) Usuario.PERFIL_PESSOA_DE_CONFIANCA else Usuario.PERFIL_PACIENTE
        val erroValidacao = validarCamposCadastro(
            nome,
            telefone,
            email,
            senha,
            confirmacaoSenha,
            codigoPaciente,
            perfilSolicitado == Usuario.PERFIL_PESSOA_DE_CONFIANCA
        )
        if (erroValidacao != null) {
            _uiState.update { it.copy(erro = erroValidacao) }
            return
        }

        _uiState.update { it.copy(isLoading = true, erro = null, sucessoMensagem = null) }

        viewModelScope.launch {
            Log.d(TAG, "cadastrar: Disparando cadastro para $email")
            val resultado = authRepository.cadastrar(
                nome = nome.trim(),
                telefone = telefone.trim(),
                email = email.trim(),
                senha = senha,
                codigoPaciente = codigoPaciente?.trim()?.takeIf { it.isNotBlank() },
                perfil = perfilSolicitado
            )
            resultado.fold(
                onSuccess = { usuario ->
                    Log.d(TAG, "cadastrar: Sucesso para UID: ${usuario.id}")
                    codigosPacienteVerificados.remove(usuario.id)
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
                    Log.e(TAG, "cadastrar: Falha no fluxo de cadastro: ${falha.message}", falha)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            erro = mensagemFalhaSegura(falha, "Não foi possível concluir o cadastro. Tente novamente.")
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
                            erro = mensagemFalhaSegura(falha, "Falha ao solicitar recuperação de senha.")
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

    private fun mensagemFalhaSegura(falha: Throwable, fallback: String): String =
        (falha as? AuthRepositoryException)?.mensagemUsuario ?: fallback

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
        telefone: String,
        email: String,
        senha: String,
        confirmacaoSenha: String,
        codigoPaciente: String? = null,
        cadastroPessoaDeConfianca: Boolean = false
    ): String? {
        if (nome.isBlank() || nome.trim().length < 2) {
            return "O nome deve conter pelo menos 2 caracteres."
        }
        if (telefone.filter(Char::isDigit).length < 10) {
            return "Informe um telefone válido com DDD."
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
        if (cadastroPessoaDeConfianca && codigoPaciente.isNullOrBlank()) {
            return "Informe o código do paciente."
        }
        if (cadastroPessoaDeConfianca && !Regex("^TMA-[A-Z0-9]{6}$").matches(codigoPaciente.orEmpty().trim().uppercase())) {
            return "Informe um código de paciente válido (ex.: TMA-7K4P92)."
        }
        return null
    }
}
