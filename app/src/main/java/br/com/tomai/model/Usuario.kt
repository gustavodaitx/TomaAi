package br.com.tomai.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Representa o usuário do sistema TomaAí.
 * Mapeado diretamente para o documento Firestore na coleção /usuarios/{id}.
 */
@IgnoreExtraProperties
data class Usuario(
    @DocumentId
    val id: String = "",

    val nome: String = "",

    val email: String = "",

    val perfil: String = PERFIL_PACIENTE,

    val ativo: Boolean = true,

    val criadoEm: Long = System.currentTimeMillis(),

    @get:PropertyName("asaasCustomerId")
    @set:PropertyName("asaasCustomerId")
    var asaasCustomerId: String? = null,

    @get:PropertyName("responsavelPadraoId")
    @set:PropertyName("responsavelPadraoId")
    var responsavelPadraoId: String? = null
) {
    companion object {
        const val PERFIL_PACIENTE = "PACIENTE"
        const val PERFIL_CUIDADOR = "CUIDADOR"
        const val PERFIL_ADMIN = "ADMIN"
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "nome" to nome,
            "email" to email,
            "perfil" to perfil,
            "ativo" to ativo,
            "criadoEm" to criadoEm,
            "asaasCustomerId" to asaasCustomerId,
            "responsavelPadraoId" to responsavelPadraoId
        )
    }
}
