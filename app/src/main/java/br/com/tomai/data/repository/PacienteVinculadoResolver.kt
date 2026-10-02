package br.com.tomai.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

internal class PacienteVinculadoResolver(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    suspend fun resolverUidPaciente(usuarioIdSolicitado: String): String? {
        val uidLogado = auth.currentUser?.uid
            ?: throw IllegalStateException("Sessão expirada. Faça login novamente.")
        if (usuarioIdSolicitado.isNotBlank() && usuarioIdSolicitado != uidLogado) {
            throw IllegalStateException("O usuário solicitado não corresponde à sessão autenticada.")
        }

        val usuario = firestore.collection("usuarios").document(uidLogado).get().await()
        if (!usuario.exists()) {
            throw IllegalStateException("O perfil do usuário autenticado não foi encontrado.")
        }

        return when (usuario.getString("perfil")) {
            "PACIENTE" -> uidLogado
            "PESSOA_DE_CONFIANCA", "CUIDADOR" -> {
                val vinculo = firestore.collection("vinculos")
                    .whereEqualTo("cuidadorUid", uidLogado)
                    .whereEqualTo("ativo", true)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
                vinculo?.getString("pacienteUid")?.takeIf { it.isNotBlank() }
            }
            else -> throw IllegalStateException("O perfil autenticado não pode consultar dados de medicamentos.")
        }
    }
}
