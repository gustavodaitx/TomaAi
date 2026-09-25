package br.com.tomai.model

import com.google.firebase.firestore.DocumentId

data class PessoaConfianca(
    @DocumentId val id: String = "",
    val usuarioId: String = "",
    val nome: String = "",
    val email: String = "",
    val telefone: String = "",
    val aceitouReceberAvisos: Boolean = false
) {
    companion object {
        private val EMAIL_REGEX = Regex(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            RegexOption.IGNORE_CASE
        )

        fun emailValido(email: String): Boolean = EMAIL_REGEX.matches(email.trim())
    }
}
