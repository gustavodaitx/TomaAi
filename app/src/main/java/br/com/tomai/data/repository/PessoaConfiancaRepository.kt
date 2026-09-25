package br.com.tomai.data.repository

import br.com.tomai.model.PessoaConfianca
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PessoaConfiancaRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun listar(usuarioId: String): List<PessoaConfianca> = firestore
        .collection(COLECAO).whereEqualTo("usuarioId", usuarioId).get().await()
        .documents.mapNotNull { it.toObject(PessoaConfianca::class.java)?.copy(id = it.id) }

    suspend fun salvar(usuarioId: String, pessoa: PessoaConfianca): String {
        require(usuarioId.isNotBlank()) { "Usuário não identificado." }
        val colecao = firestore.collection(COLECAO)
        val ref = pessoa.id.takeIf(String::isNotBlank)?.let(colecao::document) ?: colecao.document()
        ref.set(pessoa.copy(id = ref.id, usuarioId = usuarioId)).await()
        return ref.id
    }

    suspend fun definirPadrao(usuarioId: String, pessoaId: String) {
        firestore.collection("usuarios").document(usuarioId)
            .update("responsavelPadraoId", pessoaId).await()
    }

    suspend fun remover(usuarioId: String, pessoaId: String) {
        val userRef = firestore.collection("usuarios").document(usuarioId)
        firestore.runTransaction { transaction ->
            val user = transaction.get(userRef)
            transaction.delete(firestore.collection(COLECAO).document(pessoaId))
            if (user.getString("responsavelPadraoId") == pessoaId) {
                transaction.update(userRef, "responsavelPadraoId", null)
            }
        }.await()
    }

    companion object { private const val COLECAO = "pessoas_confianca" }
}
