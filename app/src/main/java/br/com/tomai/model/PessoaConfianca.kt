package br.com.tomai.model

import com.google.firebase.firestore.DocumentId

data class PessoaConfianca(
    @DocumentId val id: String = "",
    val usuarioId: String = "",
    val nome: String = "",
    val email: String = "",
    val telefone: String = "",
    val aceitouReceberAvisos: Boolean = false
)
