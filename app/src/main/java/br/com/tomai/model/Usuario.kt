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

    val telefone: String = "",

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
    var responsavelPadraoId: String? = null,

    val codigoVinculo: String? = null,
    val pacienteUid: String? = null
) {
    companion object {
        const val PERFIL_PACIENTE = "PACIENTE"
        const val PERFIL_CUIDADOR = "CUIDADOR"
        const val PERFIL_PESSOA_DE_CONFIANCA = "PESSOA_DE_CONFIANCA"
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

        fun fromFirestoreData(
            uid: String,
            data: Map<String, Any?>,
            defaultEmail: String? = null,
            defaultNome: String? = null,
            criadoEm: Timestamp? = null
        ): Usuario {
            val ativo = when (val valor = data["ativo"]) {
                is Boolean -> valor
                is Number -> valor.toInt() != 0
                is String -> valor.toBoolean()
                else -> true
            }
            return Usuario(
                id = uid,
                nome = data["nome"] as? String ?: defaultNome ?: "Usuário TomaAí",
                telefone = data["telefone"] as? String ?: "",
                email = data["email"] as? String ?: defaultEmail ?: "",
                perfil = data["perfil"] as? String ?: PERFIL_PACIENTE,
                ativo = ativo,
                criadoEm = criadoEm ?: Timestamp.now(),
                asaasCustomerId = data["asaasCustomerId"] as? String,
                responsavelPadraoId = data["responsavelPadraoId"] as? String,
                codigoVinculo = data["codigoVinculo"] as? String,
                pacienteUid = data["pacienteUid"] as? String
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
        if (telefone.isNotBlank()) {
            map["telefone"] = telefone
        }
        if (codigoVinculo != null) {
            map["codigoVinculo"] = codigoVinculo
        }
        if (pacienteUid != null) map["pacienteUid"] = pacienteUid
        if (asaasCustomerId != null) {
            map["asaasCustomerId"] = asaasCustomerId
        }
        if (responsavelPadraoId != null) {
            map["responsavelPadraoId"] = responsavelPadraoId
        }
        return map
    }
}

