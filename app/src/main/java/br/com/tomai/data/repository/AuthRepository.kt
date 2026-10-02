package br.com.tomai.data.repository

import br.com.tomai.model.Usuario
import kotlinx.coroutines.flow.Flow

class AuthRepositoryException(
    val mensagemUsuario: String,
    val cadastroPendente: Boolean = false
) : Exception(mensagemUsuario)

/**
 * Interface do repositório de autenticação e gerenciamento de perfil de usuário.
 */
interface AuthRepository {
    val usuarioAtualFlow: Flow<Usuario?>

    fun obterUsuarioAtualId(): String?

    fun estaAutenticado(): Boolean

    suspend fun login(email: String, senha: String): Result<Usuario>

    suspend fun cadastrar(nome: String, telefone: String, email: String, senha: String, codigoPaciente: String? = null): Result<Usuario>

    suspend fun garantirCodigoPaciente(uid: String): Result<String>

    suspend fun recuperarSenha(email: String): Result<Unit>

    suspend fun logout()

    suspend fun buscarPerfil(uid: String): Result<Usuario?>

    suspend fun salvarPerfil(usuario: Usuario): Result<Unit>

    fun observarPerfil(uid: String): Flow<Usuario?>
}

