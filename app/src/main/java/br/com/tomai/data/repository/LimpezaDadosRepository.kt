package br.com.tomai.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class ResultadoLimpeza(val removidosPorColecao: Map<String, Int>) {
    val totalRemovido: Int get() = removidosPorColecao.values.sum()
}

class LimpezaDadosRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    suspend fun limparDadosMedicacao(): Result<ResultadoLimpeza> = try {
        check(!auth.currentUser?.uid.isNullOrBlank()) { "Sessão expirada. Faça login novamente." }
        val resposta = functions.getHttpsCallable("limparDadosMedicacao").call().await().data as? Map<*, *>
            ?: error("O servidor não retornou o resultado da limpeza.")
        val colecoes = resposta["removidosPorColecao"] as? Map<*, *>
            ?: error("O servidor retornou um resultado inválido.")
        Result.success(ResultadoLimpeza(colecoes.entries.associate { (nome, quantidade) ->
            nome.toString() to (quantidade as? Number)?.toInt().orZero()
        }))
    } catch (error: Exception) {
        Result.failure(IllegalStateException(
            "A limpeza não foi concluída integralmente. Algumas coleções podem já ter sido limpas; revise e tente novamente.",
            error
        ))
    }

    private fun Int?.orZero(): Int = this ?: 0
}
