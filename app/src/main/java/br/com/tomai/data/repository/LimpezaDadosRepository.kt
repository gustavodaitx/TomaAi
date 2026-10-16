package br.com.tomai.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
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
        val nomesEsperados = setOf("medicamentos", "horarios_medicamentos", "doses", "alertas")
        check(nomesEsperados.all(colecoes::containsKey)) { "O servidor não confirmou todas as coleções." }
        val removidos = nomesEsperados.associateWith { nome ->
            val quantidade = colecoes[nome] as? Number
                ?: error("O servidor retornou uma contagem inválida para $nome.")
            quantidade.toInt().also { check(it >= 0) { "Contagem inválida para $nome." } }
        }
        Result.success(ResultadoLimpeza(removidos))
    } catch (error: Exception) {
        val functionsError = error as? FirebaseFunctionsException
        val mensagemServidor = functionsError?.details?.toString()
            ?.takeIf { it.isNotBlank() && it != functionsError.code.name }
            ?: functionsError?.message?.takeIf { it.isNotBlank() && it != functionsError.code.name }
        val detalhe = when (functionsError?.code) {
            FirebaseFunctionsException.Code.NOT_FOUND -> mensagemServidor
                ?: "NOT_FOUND: a função de limpeza não foi encontrada no Firebase. Confirme se limparDadosMedicacao foi implantada no projeto e região configurados. Nenhum dado foi apagado."
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Sessão expirada. Entre novamente antes de limpar os dados."
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> mensagemServidor
                ?: "O Firebase recusou a limpeza para esta conta. Nenhum dado foi apagado."
            else -> mensagemServidor
                ?: error.cause?.localizedMessage
                ?: error.localizedMessage
                ?: "Falha de conexão ou permissão."
        }
        Result.failure(IllegalStateException(
            "A limpeza não foi concluída integralmente. $detalhe",
            error
        ))
    }
}
