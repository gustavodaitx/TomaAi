package br.com.tomai.viewmodel

import br.com.tomai.data.repository.AuthRepository
import br.com.tomai.model.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = AuthViewModel(authRepository = fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun login_comEmailVazio_deveRetornarErroDeValidacao() = runTest {
        viewModel.login(email = "", senha = "123456")

        val state = viewModel.uiState.value
        assertEquals("O e-mail é obrigatório.", state.erro)
        assertFalse(state.isLoading)
        assertFalse(state.estaAutenticado)
    }

    @Test
    fun login_comEmailInvalido_deveRetornarErroDeFormato() = runTest {
        viewModel.login(email = "emailinvalido", senha = "123456")

        val state = viewModel.uiState.value
        assertEquals("Digite um e-mail válido.", state.erro)
        assertFalse(state.isLoading)
    }

    @Test
    fun login_comSenhaVazia_deveRetornarErroDeSenhaObrigatoria() = runTest {
        viewModel.login(email = "teste@exemplo.com", senha = "")

        val state = viewModel.uiState.value
        assertEquals("A senha é obrigatória.", state.erro)
        assertFalse(state.isLoading)
    }

    @Test
    fun login_comCredenciaisValidas_deveAutenticarComSucesso() = runTest {
        var callbackSucessoChamado = false
        viewModel.login(
            email = "usuario@tomai.com",
            senha = "123456",
            onSucesso = { callbackSucessoChamado = true }
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.estaAutenticado)
        assertNotNull(state.usuario)
        assertEquals("usuario@tomai.com", state.usuario?.email)
        assertNull(state.erro)
        assertFalse(state.isLoading)
        assertTrue(callbackSucessoChamado)
    }

    @Test
    fun cadastrar_comNomeCurto_deveRetornarErro() = runTest {
        viewModel.cadastrar(
            nome = "A",
            email = "teste@tomai.com",
            senha = "123456",
            confirmacaoSenha = "123456"
        )

        val state = viewModel.uiState.value
        assertEquals("O nome deve conter pelo menos 2 caracteres.", state.erro)
    }

    @Test
    fun cadastrar_comSenhasDiferentes_deveRetornarErroDeIncompatibilidade() = runTest {
        viewModel.cadastrar(
            nome = "Maria Silva",
            email = "maria@tomai.com",
            senha = "123456",
            confirmacaoSenha = "654321"
        )

        val state = viewModel.uiState.value
        assertEquals("As senhas não coincidem.", state.erro)
    }

    @Test
    fun cadastrar_comSenhaCurta_deveRetornarErroDeTamanhoMinimo() = runTest {
        viewModel.cadastrar(
            nome = "Maria Silva",
            email = "maria@tomai.com",
            senha = "123",
            confirmacaoSenha = "123"
        )

        val state = viewModel.uiState.value
        assertEquals("A senha deve ter no mínimo 6 caracteres.", state.erro)
    }

    @Test
    fun cadastrar_comDadosValidos_deveCriarUsuarioComSucesso() = runTest {
        var callbackSucessoChamado = false
        viewModel.cadastrar(
            nome = "Carlos Souza",
            email = "carlos@tomai.com",
            senha = "senhaSegura123",
            confirmacaoSenha = "senhaSegura123",
            onSucesso = { callbackSucessoChamado = true }
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.estaAutenticado)
        assertNotNull(state.usuario)
        assertEquals("Carlos Souza", state.usuario?.nome)
        assertEquals("carlos@tomai.com", state.usuario?.email)
        assertEquals("Conta criada com sucesso!", state.sucessoMensagem)
        assertTrue(callbackSucessoChamado)
    }

    @Test
    fun recuperarSenha_comEmailValido_deveDefinirMensagemDeSucesso() = runTest {
        viewModel.recuperarSenha("carlos@tomai.com")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.sucessoMensagem)
        assertNull(state.erro)
    }

    @Test
    fun logout_deveLimparEstadoDeAutenticacao() = runTest {
        viewModel.login(email = "usuario@tomai.com", senha = "123456")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.estaAutenticado)

        viewModel.logout()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.estaAutenticado)
        assertNull(state.usuario)
    }
}

/**
 * Repositório falso para isolar testes unitários do Firebase.
 */
class FakeAuthRepository : AuthRepository {
    private val _usuarioAtualFlow = MutableStateFlow<Usuario?>(null)
    override val usuarioAtualFlow: Flow<Usuario?> = _usuarioAtualFlow

    var deveFalhar: Boolean = false
    var mensagemFalha: String = "Erro simulado"

    override fun obterUsuarioAtualId(): String? = _usuarioAtualFlow.value?.id

    override fun estaAutenticado(): Boolean = _usuarioAtualFlow.value != null

    override suspend fun login(email: String, senha: String): Result<Usuario> {
        if (deveFalhar) return Result.failure(Exception(mensagemFalha))
        val user = Usuario(
            id = "mock_uid_123",
            nome = "Usuário Teste",
            email = email,
            perfil = Usuario.PERFIL_PACIENTE,
            ativo = true
        )
        _usuarioAtualFlow.value = user
        return Result.success(user)
    }

    override suspend fun cadastrar(nome: String, email: String, senha: String): Result<Usuario> {
        if (deveFalhar) return Result.failure(Exception(mensagemFalha))
        val user = Usuario(
            id = "mock_uid_cadastrado",
            nome = nome,
            email = email,
            perfil = Usuario.PERFIL_PACIENTE,
            ativo = true
        )
        _usuarioAtualFlow.value = user
        return Result.success(user)
    }

    override suspend fun recuperarSenha(email: String): Result<Unit> {
        if (deveFalhar) return Result.failure(Exception(mensagemFalha))
        return Result.success(Unit)
    }

    override suspend fun logout() {
        _usuarioAtualFlow.value = null
    }

    override suspend fun buscarPerfil(uid: String): Result<Usuario?> {
        return Result.success(_usuarioAtualFlow.value)
    }

    override suspend fun salvarPerfil(usuario: Usuario): Result<Unit> {
        _usuarioAtualFlow.value = usuario
        return Result.success(Unit)
    }
}
