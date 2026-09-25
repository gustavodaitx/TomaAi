package br.com.tomai.ui.navigation

/**
 * Rotas de navegação da aplicação TomaAí.
 */
sealed class Screen(val rota: String) {
    data object Login : Screen("login")
    data object Cadastro : Screen("cadastro")
    data object RecuperarSenha : Screen("recuperar_senha")
    data object Home : Screen("home")
    data object DosesDia : Screen("doses_dia")
    data object Medicamentos : Screen("medicamentos")
    data object MedicamentoForm : Screen("medicamento_form/{medicamentoId}") {
        fun criarRota(medicamentoId: String) = "medicamento_form/$medicamentoId"
    }
}
