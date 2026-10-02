package br.com.tomai.data.repository

import android.util.Log
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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementação do repositório de autenticação utilizando Firebase Auth e Cloud Firestore.
 * Sincroniza de ponta a ponta com a coleção /usuarios/{uid} em tempo real.
 */
class AuthRepositoryImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : AuthRepository {

    companion object {
        const val COLECAO_USUARIOS = "usuarios"
        private const val TAG = "TomaAi_AuthFirestore"
    }

    override val usuarioAtualFlow: Flow<Usuario?> = callbackFlow {
        var firestoreListener: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            firestoreListener?.remove()
            firestoreListener = null

            if (user == null) {
                Log.d(TAG, "usuarioAtualFlow: Nenhum usuário autenticado no Auth.")
                trySend(null)
            } else {
                Log.d(TAG, "usuarioAtualFlow: Usuário autenticado: UID=${user.uid}, Email=${user.email}")
                // Emite imediatamente estado preliminar com dados do Auth para evitar UI em estado nulo
                val usuarioInicial = Usuario(
                    id = user.uid,
                    nome = user.displayName?.ifBlank { "Usuário TomaAí" } ?: "Usuário TomaAí",
                    email = user.email ?: "",
                    perfil = Usuario.PERFIL_PACIENTE,
                    ativo = true,
                    criadoEm = Timestamp.now()
                )
                trySend(usuarioInicial)

                val docRef = firestore.collection(COLECAO_USUARIOS).document(user.uid)
                Log.d(TAG, "usuarioAtualFlow: Registrando addSnapshotListener para /usuarios/${user.uid}")

                firestoreListener = docRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "usuarioAtualFlow: Erro no snapshotListener para UID ${user.uid}: ${error.message}", error)
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
                        Log.d(TAG, "usuarioAtualFlow: Documento recebido com sucesso para UID: ${user.uid}")
                        val usuario = mapearDocumentoParaUsuario(
                            snapshot = snapshot,
                            defaultUid = user.uid,
                            defaultEmail = user.email,
                            defaultNome = user.displayName
                        )
                        trySend(usuario)
                    } else if (snapshot != null && !snapshot.exists()) {
                        Log.d(TAG, "usuarioAtualFlow: Documento /usuarios/${user.uid} inexiste. Criando documento padrão com SetOptions.merge()...")
                        val novoUsuario = Usuario.criarPadrao(
                            uid = user.uid,
                            email = user.email ?: "",
                            nome = user.displayName
                        )
                        docRef.set(novoUsuario.toMap(), SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d(TAG, "usuarioAtualFlow: Documento padrão criado com sucesso para UID: ${user.uid}")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "usuarioAtualFlow: Falha ao criar documento padrão para UID ${user.uid}: ${e.message}", e)
                            }
                        trySend(novoUsuario)
                    }
                }
            }
        }

        auth.addAuthStateListener(authListener)

        awaitClose {
            Log.d(TAG, "usuarioAtualFlow: Encerrando observação e removendo listeners.")
            auth.removeAuthStateListener(authListener)
            firestoreListener?.remove()
        }
    }

    override fun observarPerfil(uid: String): Flow<Usuario?> = callbackFlow {
        if (uid.isBlank()) {
            Log.e(TAG, "observarPerfil: Chamado com UID vazio.")
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection(COLECAO_USUARIOS).document(uid)
        Log.d(TAG, "observarPerfil: Registrando addSnapshotListener para UID: $uid")

        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "observarPerfil: Erro no snapshotListener para UID $uid: ${error.message}", error)
                val fallback = if (auth.currentUser?.uid == uid) {
                    Usuario(
                        id = uid,
                        nome = auth.currentUser?.displayName?.ifBlank { "Usuário TomaAí" } ?: "Usuário TomaAí",
                        email = auth.currentUser?.email ?: "",
                        perfil = Usuario.PERFIL_PACIENTE,
                        ativo = true,
                        criadoEm = Timestamp.now()
                    )
                } else null
                trySend(fallback)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "observarPerfil: Snapshot existente para UID: $uid")
                val usuario = mapearDocumentoParaUsuario(snapshot, uid, auth.currentUser?.email, auth.currentUser?.displayName)
                trySend(usuario)
            } else if (snapshot != null && !snapshot.exists()) {
                Log.d(TAG, "observarPerfil: Documento não existe para UID: $uid. Criando documento padrão com SetOptions.merge()...")
                val usuarioPadrao = Usuario.criarPadrao(
                    uid = uid,
                    email = auth.currentUser?.email ?: "",
                    nome = auth.currentUser?.displayName
                )
                docRef.set(usuarioPadrao.toMap(), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "observarPerfil: Documento padrão criado com sucesso para UID: $uid")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "observarPerfil: Falha ao criar documento padrão para UID $uid: ${e.message}", e)
                    }
                trySend(usuarioPadrao)
            }
        }

        awaitClose {
            Log.d(TAG, "observarPerfil: Removendo snapshotListener para UID: $uid")
            registration.remove()
        }
    }

    override fun obterUsuarioAtualId(): String? {
        val uid = auth.currentUser?.uid
        Log.d(TAG, "obterUsuarioAtualId: Retornando UID do Auth: $uid")
        return uid
    }

    override fun estaAutenticado(): Boolean {
        val autenticado = auth.currentUser != null
        Log.d(TAG, "estaAutenticado: $autenticado")
        return autenticado
    }

    override suspend fun login(email: String, senha: String): Result<Usuario> {
        return try {
            Log.d(TAG, "login: Iniciando autenticação para o e-mail: ${email.trim()}")
            val authResult = auth.signInWithEmailAndPassword(email.trim(), senha).await()
            val firebaseUser = auth.currentUser ?: authResult.user
                ?: return Result.failure(Exception("Falha ao obter usuário autenticado no Firebase Auth."))

            val uid = firebaseUser.uid
            Log.d(TAG, "login: Autenticação Auth bem-sucedida! UID: $uid. Sincronizando com Firestore...")

            val docRef = firestore.collection(COLECAO_USUARIOS).document(uid)
            val snapshot = try {
                docRef.get().await()
            } catch (e: Exception) {
                Log.e(TAG, "login: Erro ao buscar documento /usuarios/$uid no Firestore: ${e.message}", e)
                null
            }

            val usuario = if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "login: Documento /usuarios/$uid encontrado no Firestore. Atualizando campos básicos com SetOptions.merge()...")
                val usuarioExistente = mapearDocumentoParaUsuario(
                    snapshot = snapshot,
                    defaultUid = uid,
                    defaultEmail = firebaseUser.email ?: email.trim(),
                    defaultNome = firebaseUser.displayName
                )
                val atualizacao = hashMapOf<String, Any?>(
                    "id" to uid,
                    "email" to (firebaseUser.email ?: email.trim()),
                    "ativo" to true
                )
                if (!firebaseUser.displayName.isNullOrBlank()) {
                    atualizacao["nome"] = firebaseUser.displayName
                }
                docRef.set(atualizacao, SetOptions.merge()).await()
                Log.d(TAG, "login: Documento /usuarios/$uid atualizado com sucesso via SetOptions.merge().")
                usuarioExistente
            } else {
                Log.d(TAG, "login: Documento /usuarios/$uid não existe. Criando documento padrão via SetOptions.merge()...")
                val novoUsuario = Usuario.criarPadrao(
                    uid = uid,
                    email = firebaseUser.email ?: email.trim(),
                    nome = firebaseUser.displayName
                )
                docRef.set(novoUsuario.toMap(), SetOptions.merge()).await()
                Log.d(TAG, "login: Documento padrão criado no Firestore com sucesso para UID: $uid")
                novoUsuario
            }

            Result.success(usuario)
        } catch (e: Exception) {
            Log.e(TAG, "login: Falha durante o processo de login: ${e.message}", e)
            Result.failure(AuthRepositoryException(mapearMensagemErro(e)))
        }
    }

    override suspend fun cadastrar(nome: String, telefone: String, email: String, senha: String, codigoPaciente: String?): Result<Usuario> {
        var usuarioAutenticado = auth.currentUser
        return try {
            if (usuarioAutenticado != null && !usuarioAutenticado.email.equals(email.trim(), ignoreCase = true)) {
                return Result.failure(AuthRepositoryException("Existe uma sessao ativa com outro e-mail. Encerre-a antes de cadastrar."))
            }

            if (usuarioAutenticado == null) {
                usuarioAutenticado = try {
                    val authResult = auth.createUserWithEmailAndPassword(email.trim(), senha).await()
                    auth.currentUser ?: authResult.user
                } catch (_: FirebaseAuthUserCollisionException) {
                    val loginResult = auth.signInWithEmailAndPassword(email.trim(), senha).await()
                    auth.currentUser ?: loginResult.user
                }
            }
            val firebaseUser = usuarioAutenticado
                ?: throw IllegalStateException("Nao foi possivel confirmar a conta no Firebase Authentication.")

            if (firebaseUser.displayName.isNullOrBlank()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(nome.trim())
                    .build()
                runCatching { firebaseUser.updateProfile(profileUpdates).await() }
                    .onFailure { Log.w(TAG, "cadastrar: Nao foi possivel atualizar o nome de exibicao.", it) }
            }

            val pessoaDeConfianca = !codigoPaciente.isNullOrBlank()
            val resultadoFuncao = functions
                .getHttpsCallable(if (pessoaDeConfianca) "criarPerfilPessoaDeConfianca" else "criarPerfilPaciente")
                .call(buildMap {
                    put("nome", nome.trim())
                    put("telefone", telefone.trim())
                    if (pessoaDeConfianca) put("codigoPaciente", codigoPaciente!!.trim().uppercase())
                })
                .await()
            val dadosPerfil = resultadoFuncao.data as? Map<*, *>
                ?: throw IllegalStateException("Resposta invalida ao concluir cadastro.")
            val codigoVinculo = (dadosPerfil["codigoPaciente"] as? String) ?: (dadosPerfil["codigoVinculo"] as? String)
            val pacienteUid = dadosPerfil["pacienteUid"] as? String
            val codigoPacienteDigitadoSalvo = dadosPerfil["codigoPacienteDigitado"] as? String
            val nomeSalvo = dadosPerfil["nome"] as? String
            val telefoneSalvo = dadosPerfil["telefone"] as? String
            val emailSalvo = dadosPerfil["email"] as? String
            val perfilEsperado = if (pessoaDeConfianca) Usuario.PERFIL_PESSOA_DE_CONFIANCA else Usuario.PERFIL_PACIENTE
            if (nomeSalvo == null || telefoneSalvo == null || emailSalvo == null || dadosPerfil["perfil"] != perfilEsperado ||
                (pessoaDeConfianca && (pacienteUid.isNullOrBlank() || codigoPacienteDigitadoSalvo.isNullOrBlank() || codigoVinculo != null)) ||
                (!pessoaDeConfianca && codigoVinculo.isNullOrBlank())) {
                throw IllegalStateException("Resposta invalida ao concluir cadastro.")
            }

            Result.success(
                Usuario(
                    id = firebaseUser.uid,
                    nome = nomeSalvo,
                    telefone = telefoneSalvo,
                    email = emailSalvo,
                    perfil = perfilEsperado,
                    ativo = true,
                    criadoEm = Timestamp.now(),
                    codigoVinculo = codigoVinculo,
                    pacienteUid = pacienteUid,
                    codigoPaciente = codigoVinculo,
                    codigoPacienteDigitado = codigoPacienteDigitadoSalvo,
                    aceitouReceberAvisos = dadosPerfil["aceitouReceberAvisos"] as? Boolean ?: true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "cadastrar: Falha no fluxo de cadastro: ${e.message}", e)
            val codigoFunction = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.code
            val mensagemDefinitiva = when (codigoFunction) {
                com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND -> "Código do paciente inválido. Confira o código e tente novamente."
                com.google.firebase.functions.FirebaseFunctionsException.Code.ALREADY_EXISTS -> "Este cadastro já está vinculado a um paciente."
                com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "Não foi possível vincular a conta com este código."
                com.google.firebase.functions.FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                    "Confira nome e telefone e tente novamente."
                com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                    "Nao foi possivel gerar o codigo agora. Tente novamente."
                else -> null
            }
            val pendente = usuarioAutenticado != null || auth.currentUser != null
            val mensagem = mensagemDefinitiva
                ?: if (pendente) {
                    "Sua conta foi criada, mas nao foi possivel confirmar o cadastro. Tente novamente."
                } else {
                    mapearMensagemErro(e)
                }
            Result.failure(AuthRepositoryException(mensagem, cadastroPendente = pendente))
        }
    }
    override suspend fun recuperarSenha(email: String): Result<Unit> {
        return try {
            Log.d(TAG, "recuperarSenha: Enviando e-mail de recuperação para: ${email.trim()}")
            auth.sendPasswordResetEmail(email.trim()).await()
            Log.d(TAG, "recuperarSenha: E-mail enviado com sucesso.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "recuperarSenha: Erro ao enviar recuperação de senha: ${e.message}", e)
            Result.failure(AuthRepositoryException(mapearMensagemErro(e)))
        }
    }

    override suspend fun logout() {
        Log.d(TAG, "logout: Encerrando sessão no Firebase Auth.")
        auth.signOut()
    }

    override suspend fun buscarPerfil(uid: String): Result<Usuario?> {
        return try {
            Log.d(TAG, "buscarPerfil: Buscando /usuarios/$uid no Firestore...")
            val docRef = firestore.collection(COLECAO_USUARIOS).document(uid)
            val snapshot = docRef.get().await()

            if (snapshot.exists()) {
                Log.d(TAG, "buscarPerfil: Documento encontrado para UID: $uid")
                val usuario = mapearDocumentoParaUsuario(snapshot, uid, auth.currentUser?.email, auth.currentUser?.displayName)
                Result.success(usuario)
            } else {
                Log.d(TAG, "buscarPerfil: Documento inexistente para UID: $uid. Criando documento padrão...")
                val usuarioPadrao = Usuario.criarPadrao(
                    uid = uid,
                    email = auth.currentUser?.email ?: "",
                    nome = auth.currentUser?.displayName
                )
                docRef.set(usuarioPadrao.toMap(), SetOptions.merge()).await()
                Log.d(TAG, "buscarPerfil: Documento padrão criado com sucesso para UID: $uid")
                Result.success(usuarioPadrao)
            }
        } catch (e: Exception) {
            Log.e(TAG, "buscarPerfil: Erro ao buscar perfil para UID $uid: ${e.message}", e)
            Result.failure(AuthRepositoryException(mapearMensagemErro(e)))
        }
    }

    override suspend fun salvarPerfil(usuario: Usuario): Result<Unit> {
        return try {
            val id = usuario.id.ifBlank {
                obterUsuarioAtualId() ?: throw IllegalStateException("UID do usuário não identificado.")
            }
            val usuarioAjustado = usuario.copy(id = id)
            Log.d(TAG, "salvarPerfil: Salvando perfil para UID: $id no Firestore...")
            firestore.collection(COLECAO_USUARIOS)
                .document(id)
                .set(usuarioAjustado.toMap(), SetOptions.merge())
                .await()
            Log.d(TAG, "salvarPerfil: Perfil salvo com sucesso para UID: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "salvarPerfil: Erro ao salvar perfil: ${e.message}", e)
            Result.failure(AuthRepositoryException(mapearMensagemErro(e)))
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
        val uid = snapshot.id.ifBlank { defaultUid }
        return try {
            val objeto = try {
                snapshot.toObject(Usuario::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "mapearDocumentoParaUsuario: toObject() falhou devido a inconsistência nos campos: ${e.message}. Executando fallback manual.")
                null
            }

            if (objeto != null) {
                val nomeSeguro = if (objeto.nome.isNotBlank()) {
                    objeto.nome
                } else {
                    (snapshot.get("nome") as? String) ?: defaultNome ?: "Usuário TomaAí"
                }

                val emailSeguro = if (objeto.email.isNotBlank()) {
                    objeto.email
                } else {
                    (snapshot.get("email") as? String) ?: defaultEmail ?: ""
                }

                objeto.copy(
                    id = if (objeto.id.isNotBlank()) objeto.id else uid,
                    nome = (snapshot.get("nome") as? String)?.takeIf { it.isNotBlank() } ?: nomeSeguro,
                    telefone = (snapshot.get("telefone") as? String) ?: objeto.telefone,
                    email = (snapshot.get("email") as? String)?.takeIf { it.isNotBlank() } ?: emailSeguro,
                    perfil = (snapshot.get("perfil") as? String) ?: objeto.perfil,
                    ativo = (snapshot.get("ativo") as? Boolean) ?: objeto.ativo,
                    codigoVinculo = (snapshot.get("codigoPaciente") as? String)
                        ?: (snapshot.get("codigoVinculo") as? String)
                        ?: objeto.codigoVinculo,
                    codigoPaciente = (snapshot.get("codigoPaciente") as? String)
                        ?: (snapshot.get("codigoVinculo") as? String)
                        ?: objeto.codigoPaciente
                        ?: objeto.codigoVinculo,
                    codigoPacienteDigitado = (snapshot.get("codigoPacienteDigitado") as? String) ?: objeto.codigoPacienteDigitado,
                    pacienteUid = (snapshot.get("pacienteUid") as? String) ?: objeto.pacienteUid,
                    aceitouReceberAvisos = (snapshot.get("aceitouReceberAvisos") as? Boolean) ?: objeto.aceitouReceberAvisos,
                    criadoEm = extrairTimestampSeguro(snapshot, "criadoEm")
                )
            } else {
                criarUsuarioDeSnapshot(snapshot, uid, defaultEmail, defaultNome)
            }
        } catch (e: Exception) {
            Log.e(TAG, "mapearDocumentoParaUsuario: Falha geral no mapeamento: ${e.message}. Executando fallback manual.", e)
            criarUsuarioDeSnapshot(snapshot, uid, defaultEmail, defaultNome)
        }
    }

    private fun criarUsuarioDeSnapshot(
        snapshot: DocumentSnapshot,
        uid: String,
        defaultEmail: String?,
        defaultNome: String?
    ): Usuario {
        return try {
            val criadoEm = extrairTimestampSeguro(snapshot, "criadoEm")
            Usuario.fromFirestoreData(
                uid = uid,
                data = snapshot.data ?: emptyMap(),
                defaultEmail = defaultEmail,
                defaultNome = defaultNome,
                criadoEm = criadoEm
            )
        } catch (e: Exception) {
            Log.e(TAG, "criarUsuarioDeSnapshot: Erro crítico ao construir Usuario: ${e.message}", e)
            Usuario.criarPadrao(
                uid = uid,
                email = defaultEmail ?: "",
                nome = defaultNome
            ).copy(criadoEm = extrairTimestampSeguro(snapshot, "criadoEm"))
        }
    }

    /**
     * Extrai com segurança o campo [Timestamp] de um [DocumentSnapshot].
     * Valida o tipo do dado existente no Firestore:
     * - Se for Timestamp: utiliza o valor diretamente.
     * - Se for Long ou Number: converte para Timestamp dividindo os milissegundos.
     * - Se for nulo ou inválido: utiliza Timestamp.now() como fallback.
     */
    private fun extrairTimestampSeguro(
        snapshot: DocumentSnapshot,
        campo: String = "criadoEm"
    ): Timestamp {
        return try {
            when (val valor = snapshot.get(campo)) {
                is Timestamp -> valor
                is Long -> converterMillisParaTimestamp(valor)
                is Number -> converterMillisParaTimestamp(valor.toLong())
                is Date -> Timestamp(valor)
                else -> Timestamp.now()
            }
        } catch (e: Exception) {
            Log.w(TAG, "extrairTimestampSeguro: Falha ao converter campo '$campo' (${e.message}). Usando Timestamp.now() como fallback.")
            Timestamp.now()
        }
    }

    /**
     * Converte um valor numérico em milissegundos para [Timestamp],
     * dividindo em segundos e fração de nanossegundos.
     */
    private fun converterMillisParaTimestamp(millis: Long): Timestamp {
        return try {
            if (millis <= 0L) {
                Timestamp.now()
            } else if (millis > 100_000_000_000L) {
                // Formato padrão em milissegundos (ex: System.currentTimeMillis() ou Date.now())
                val segundos = millis / 1000
                val nanos = ((millis % 1000) * 1_000_000).toInt()
                Timestamp(segundos, nanos)
            } else {
                // Caso o número já esteja em segundos Unix
                Timestamp(millis, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "converterMillisParaTimestamp: Erro ao converter millis $millis: ${e.message}")
            Timestamp.now()
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
            is com.google.firebase.functions.FirebaseFunctionsException -> {
                "Nao foi possivel concluir o cadastro agora. Verifique sua conexao e tente novamente."
            }
            is FirebaseNetworkException -> {
                "Sem conexão com a internet. Verifique sua rede e tente novamente."
            }
            else -> {
                "Nao foi possivel concluir a operacao. Tente novamente."
            }
        }
    }
}
