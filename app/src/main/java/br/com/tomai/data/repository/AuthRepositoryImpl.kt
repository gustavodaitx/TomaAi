package br.com.tomai.data.repository

import br.com.tomai.model.Usuario
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementação do repositório de autenticação utilizando Firebase Auth e Cloud Firestore.
 * Sincroniza em tempo real a coleção /usuarios/{uid}.
 */
class AuthRepositoryImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    companion object {
        const val COLECAO_USUARIOS = "usuarios"
    }

    override val usuarioAtualFlow: Flow<Usuario?> = callbackFlow {
        var firestoreListener: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            // Cancela listener anterior do Firestore ao mudar o estado de autenticação
            firestoreListener?.remove()
            firestoreListener = null

            if (user == null) {
                trySend(null)
            } else {
                val docRef = firestore.collection(COLECAO_USUARIOS).document(user.uid)

                firestoreListener = docRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Trata erros de rede/permissão emitindo fallback com os dados do Auth
                        val fallback = Usuario(
                            id = user.uid,
                            nome = user.displayName?.ifBlank { "Usuário" } ?: "Usuário",
                            email = user.email ?: "",
                            perfil = Usuario.PERFIL_PACIENTE,
                            ativo = true,
                            criadoEm = Timestamp.now()
                        )
                        trySend(fallback)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val usuario = mapearDocumentoParaUsuario(
                            snapshot = snapshot,
                            defaultUid = user.uid,
                            defaultEmail = user.email,
                            defaultNome = user.displayName
                        )
                        trySend(usuario)
                    } else if (snapshot != null && !snapshot.exists()) {
                        // Tratamento de exceção/ausência: documento ainda não existe na primeira consulta
                        // Garante a criação imediata em /usuarios/{uid} com o ID correspondente ao request.auth.uid
                        val novoUsuario = Usuario(
                            id = user.uid,
                            nome = user.displayName?.ifBlank { "Usuário TomaAí" } ?: "Usuário TomaAí",
                            email = user.email ?: "",
                            perfil = Usuario.PERFIL_PACIENTE,
                            ativo = true,
                            criadoEm = Timestamp.now()
                        )
                        docRef.set(novoUsuario.toMap())
                        trySend(novoUsuario)
                    }
                }
            }
        }

        auth.addAuthStateListener(authListener)

        awaitClose {
            auth.removeAuthStateListener(authListener)
            firestoreListener?.remove()
        }
    }

    override fun observarPerfil(uid: String): Flow<Usuario?> = callbackFlow {
        val docRef = firestore.collection(COLECAO_USUARIOS).document(uid)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val usuario = mapearDocumentoParaUsuario(snapshot, uid)
                trySend(usuario)
            } else if (snapshot != null && !snapshot.exists()) {
                val usuarioPadrao = Usuario(
                    id = uid,
                    nome = "Usuário TomaAí",
                    email = auth.currentUser?.email ?: "",
                    perfil = Usuario.PERFIL_PACIENTE,
                    ativo = true,
                    criadoEm = Timestamp.now()
                )
                docRef.set(usuarioPadrao.toMap())
                trySend(usuarioPadrao)
            }
        }

        awaitClose {
            registration.remove()
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

            val docRef = firestore.collection(COLECAO_USUARIOS).document(firebaseUser.uid)
            val snapshot = docRef.get().await()

            val usuario = if (snapshot.exists()) {
                mapearDocumentoParaUsuario(
                    snapshot = snapshot,
                    defaultUid = firebaseUser.uid,
                    defaultEmail = firebaseUser.email ?: email.trim(),
                    defaultNome = firebaseUser.displayName
                )
            } else {
                // Garante a criação do documento em /usuarios/{uid} no login caso não exista
                val novoUsuario = Usuario(
                    id = firebaseUser.uid,
                    nome = firebaseUser.displayName?.ifBlank { "Usuário TomaAí" } ?: "Usuário TomaAí",
                    email = firebaseUser.email ?: email.trim(),
                    perfil = Usuario.PERFIL_PACIENTE,
                    ativo = true,
                    criadoEm = Timestamp.now()
                )
                docRef.set(novoUsuario.toMap()).await()
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

            // Atualiza displayName no Firebase Auth
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(nome.trim())
                .build()
            try {
                firebaseUser.updateProfile(profileUpdates).await()
            } catch (_: Exception) {
                // Prossegue caso a atualização de displayName no Auth falhe
            }

            val novoUsuario = Usuario(
                id = firebaseUser.uid,
                nome = nome.trim(),
                email = email.trim(),
                perfil = Usuario.PERFIL_PACIENTE,
                ativo = true,
                criadoEm = Timestamp.now()
            )

            // Salva dados no Firestore em /usuarios/{uid} com ID sendo exatamente o request.auth.uid
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
            val docRef = firestore.collection(COLECAO_USUARIOS).document(uid)
            val snapshot = docRef.get().await()

            if (snapshot.exists()) {
                val usuario = mapearDocumentoParaUsuario(snapshot, uid)
                Result.success(usuario)
            } else {
                // Cria documento caso ainda não exista para manter integridade
                val usuarioPadrao = Usuario(
                    id = uid,
                    nome = "Usuário TomaAí",
                    email = auth.currentUser?.email ?: "",
                    perfil = Usuario.PERFIL_PACIENTE,
                    ativo = true,
                    criadoEm = Timestamp.now()
                )
                docRef.set(usuarioPadrao.toMap()).await()
                Result.success(usuarioPadrao)
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapearMensagemErro(e)))
        }
    }

    override suspend fun salvarPerfil(usuario: Usuario): Result<Unit> {
        return try {
            val id = usuario.id.ifBlank {
                obterUsuarioAtualId() ?: throw IllegalStateException("UID do usuário não identificado.")
            }
            val usuarioAjustado = usuario.copy(id = id)
            firestore.collection(COLECAO_USUARIOS)
                .document(id)
                .set(usuarioAjustado.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(mapearMensagemErro(e)))
        }
    }

    /**
     * Mapeia com segurança os campos do DocumentSnapshot para o objeto Usuario.
     * Trata possíveis inconsistências de tipos evitando exceções de deserialização.
     */
    private fun mapearDocumentoParaUsuario(
        snapshot: DocumentSnapshot,
        defaultUid: String,
        defaultEmail: String? = null,
        defaultNome: String? = null
    ): Usuario {
        return try {
            snapshot.toObject(Usuario::class.java)?.copy(id = snapshot.id.ifBlank { defaultUid })
        } catch (e: Exception) {
            null
        } ?: Usuario(
            id = snapshot.id.ifBlank { defaultUid },
            nome = snapshot.getString("nome") ?: defaultNome ?: "Usuário TomaAí",
            email = snapshot.getString("email") ?: defaultEmail ?: "",
            perfil = snapshot.getString("perfil") ?: Usuario.PERFIL_PACIENTE,
            ativo = snapshot.getBoolean("ativo") ?: true,
            criadoEm = snapshot.getTimestamp("criadoEm") ?: Timestamp.now(),
            asaasCustomerId = snapshot.getString("asaasCustomerId"),
            responsavelPadraoId = snapshot.getString("responsavelPadraoId")
        )
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
