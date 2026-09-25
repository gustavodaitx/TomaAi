package br.com.tomai.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

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

    @ServerTimestamp
    val criadoEm: Timestamp? = Timestamp.now(),

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

        fun criarPadrao(
            uid: String,
            email: String,
            nome: String? = null
        ): Usuario {
            return Usuario(
                id = uid,
                nome = nome?.ifBlank { "Usuário TomaAí" } ?: "Usuário TomaAí",
                email = email,
                perfil = PERFIL_PACIENTE,
                ativo = true,
                criadoEm = Timestamp.now()
            )
        }
    }

    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to id,
            "nome" to nome,
            "email" to email,
            "perfil" to perfil,
            "ativo" to ativo,
            "criadoEm" to (criadoEm ?: Timestamp.now())
        )
        if (asaasCustomerId != null) {
            map["asaasCustomerId"] = asaasCustomerId
        }
        if (responsavelPadraoId != null) {
            map["responsavelPadraoId"] = responsavelPadraoId
        }
        return map
    }
}

