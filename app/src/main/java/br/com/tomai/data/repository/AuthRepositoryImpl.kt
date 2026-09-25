package br.com.tomai.data.repository

import br.com.tomai.model.Usuario
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementação do repositório de autenticação utilizando Firebase Auth e Cloud Firestore.
 */
class AuthRepositoryImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    companion object {
        const val COLECAO_USUARIOS = "usuarios"
    }

    override val usuarioAtualFlow: Flow<Usuario?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(null)
            } else {
                firestore.collection(COLECAO_USUARIOS)
                    .document(user.uid)
                    .get()
                    .addOnSuccessListener { documentSnapshot ->
                        val usuario = if (documentSnapshot.exists()) {
                            documentSnapshot.toObject(Usuario::class.java)?.copy(id = user.uid)
                        } else {
                            Usuario(
                                id = user.uid,
                                nome = user.displayName ?: "Usuário",
                                email = user.email ?: "",
                                perfil = Usuario.PERFIL_PACIENTE,
                                ativo = true
                            )
                        }
                        trySend(usuario)
                    }
                    .addOnFailureListener {
                        trySend(
                            Usuario(
                                id = user.uid,
                                nome = user.displayName ?: "Usuário",
                                email = user.email ?: "",
                                perfil = Usuario.PERFIL_PACIENTE,
                                ativo = true
                            )
                        )
                    }
            }
        }

        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    override fun obterUsuarioAtualId(): String? {
        return auth.currentUser?.uid
    }

    override fun estaAutenticado(): Boolean {
        return auth.currentUser != null
    }

    override suspend fun login(email: String, senha: String): Result<Usuario> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), senha).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Falha ao obter usuário autenticado."))

            val snapshot = firestore.collection(COLECAO_USUARIOS)
                .document(firebaseUser.uid)
                .get()
                .await()

            val usuario = if (snapshot.exists()) {
                snapshot.toObject(Usuario::class.java)?.copy(id = firebaseUser.uid)
                    ?: Usuario(
                        id = firebaseUser.uid,
                        nome = firebaseUser.displayName ?: "Usuário",
                        email = firebaseUser.email ?: email.trim()
                    )
            } else {
                val novoUsuario = Usuario(
                    id = firebaseUser.uid,
                    nome = firebaseUser.displayName ?: "Usuário",
                    email = firebaseUser.email ?: email.trim(),
                    perfil = Usuario.PERFIL_PACIENTE,
                    ativo = true,
                    criadoEm = System.currentTimeMillis()
                )
                salvarPerfil(novoUsuario)
                novoUsuario
            }

            Result.success(usuario)
        } catch (e: Exception) {
            Result.failure(Exception(mapearMensagemErro(e)))
        }
    }

    override suspend fun cadastrar(nome: String, email: String, senha: String): Result<Usuario> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), senha).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Falha ao criar usuário."))

            val novoUsuario = Usuario(
                id = firebaseUser.uid,
                nome = nome.trim(),
                email = email.trim(),
                perfil = Usuario.PERFIL_PACIENTE,
                ativo = true,
                criadoEm = System.currentTimeMillis()
            )

            // Salva dados no Firestore em /usuarios/{uid}
            firestore.collection(COLECAO_USUARIOS)
                .document(firebaseUser.uid)
                .set(novoUsuario.toMap())
                .await()

            Result.success(novoUsuario)
        } catch (e: Exception) {
            Result.failure(Exception(mapearMensagemErro(e)))
        }
    }

    override suspend fun recuperarSenha(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(mapearMensagemErro(e)))
        }
    }

    override suspend fun logout() {
        auth.signOut()
    }

    override suspend fun buscarPerfil(uid: String): Result<Usuario?> {
        return try {
            val snapshot = firestore.collection(COLECAO_USUARIOS)
                .document(uid)
                .get()
                .await()

            val usuario = if (snapshot.exists()) {
                snapshot.toObject(Usuario::class.java)?.copy(id = uid)
            } else {
                null
            }
            Result.success(usuario)
        } catch (e: Exception) {
            Result.failure(Exception(mapearMensagemErro(e)))
        }
    }

    override suspend fun salvarPerfil(usuario: Usuario): Result<Unit> {
        return try {
            firestore.collection(COLECAO_USUARIOS)
                .document(usuario.id)
                .set(usuario.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(mapearMensagemErro(e)))
        }
    }

    private fun mapearMensagemErro(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthInvalidCredentialsException -> {
                "E-mail ou senha inválidos. Verifique os dados e tente novamente."
            }
            is FirebaseAuthWeakPasswordException -> {
                "A senha é muito fraca. Digite pelo menos 6 caracteres com letras e números."
            }
            is FirebaseAuthUserCollisionException -> {
                "Já existe uma conta cadastrada com este endereço de e-mail."
            }
            is FirebaseAuthInvalidUserException -> {
                "Usuário não encontrado ou conta desativada."
            }
            is FirebaseNetworkException -> {
                "Sem conexão com a internet. Verifique sua rede e tente novamente."
            }
            else -> {
                exception.localizedMessage ?: "Ocorreu um erro inesperado. Tente novamente mais tarde."
            }
        }
    }
}
