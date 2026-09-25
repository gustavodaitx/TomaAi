package br.com.tomai.viewmodel

import br.com.tomai.model.Usuario

/**
 * Estado da interface para o fluxo de autenticação e perfil.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val usuario: Usuario? = null,
    val estaAutenticado: Boolean = false,
    val erro: String? = null,
    val sucessoMensagem: String? = null
)
