package br.com.tomai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import br.com.tomai.ui.cadastro.CadastroScreen
import br.com.tomai.ui.home.HomeScreen
import br.com.tomai.ui.login.LoginScreen
import br.com.tomai.ui.login.RecuperarSenhaScreen
import br.com.tomai.ui.doses.DosesDiaScreen
import br.com.tomai.ui.doses.HistoricoDosesScreen
import br.com.tomai.ui.medicamentos.MedicamentoFormScreen
import br.com.tomai.ui.medicamentos.MedicamentosListScreen
import br.com.tomai.ui.responsaveis.PessoasConfiancaScreen
import br.com.tomai.ui.assinaturas.AssinaturasScreen
import br.com.tomai.viewmodel.AuthViewModel
import br.com.tomai.viewmodel.DoseViewModel
import br.com.tomai.viewmodel.MedicamentoViewModel
import br.com.tomai.viewmodel.PessoaConfiancaViewModel
import br.com.tomai.viewmodel.AssinaturaViewModel
import br.com.tomai.viewmodel.ScreenMedicamentoId

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: AuthViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val destinoInicial = if (uiState.estaAutenticado) Screen.Home.rota else Screen.Login.rota

    NavHost(
        navController = navController,
        startDestination = destinoInicial
    ) {
        composable(Screen.Login.rota) {
            LoginScreen(
                viewModel = viewModel,
                onNavegarParaCadastro = {
                    navController.navigate(Screen.Cadastro.rota)
                },
                onNavegarParaRecuperarSenha = {
                    navController.navigate(Screen.RecuperarSenha.rota)
                },
                onLoginSucesso = {
                    navController.navigate(Screen.Home.rota) {
                        popUpTo(Screen.Login.rota) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Cadastro.rota) {
            CadastroScreen(
                viewModel = viewModel,
                onVoltarParaLogin = {
                    navController.popBackStack()
                },
                onCadastroSucesso = {
                    navController.navigate(Screen.Home.rota) {
                        popUpTo(Screen.Login.rota) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RecuperarSenha.rota) {
            RecuperarSenhaScreen(
                viewModel = viewModel,
                onVoltarParaLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Home.rota) {
            val homeDoseViewModel: DoseViewModel = viewModel()
            val homeAssinaturaViewModel: AssinaturaViewModel = viewModel()
            HomeScreen(
                viewModel = viewModel,
                doseViewModel = homeDoseViewModel,
                assinaturaViewModel = homeAssinaturaViewModel,
                onNavegarMedicamentos = {
                    navController.navigate(Screen.Medicamentos.rota)
                },
                onNavegarDosesDia = {
                    navController.navigate(Screen.DosesDia.rota)
                },
                onNavegarHistorico = { navController.navigate(Screen.HistoricoDoses.rota) },
                onNavegarPessoasConfianca = {
                    navController.navigate(Screen.PessoasConfianca.rota)
                },
                onNavegarAssinaturas = { navController.navigate(Screen.Assinaturas.rota) },
                onLogoutConcluido = {
                    navController.navigate(Screen.Login.rota) {
                        popUpTo(Screen.Home.rota) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.DosesDia.rota) {
            val doseViewModel: DoseViewModel = viewModel()
            val uid = viewModel.obterUidAutenticado().orEmpty()
            DosesDiaScreen(
                usuarioId = uid,
                viewModel = doseViewModel,
                onVoltar = { navController.popBackStack() }
            )
        }

        composable(Screen.HistoricoDoses.rota) {
            val doseViewModel: DoseViewModel = viewModel()
            HistoricoDosesScreen(
                usuarioId = viewModel.obterUidAutenticado().orEmpty(),
                viewModel = doseViewModel,
                onVoltar = { navController.popBackStack() }
            )
        }

        composable(Screen.PessoasConfianca.rota) {
            val responsavelViewModel: PessoaConfiancaViewModel = viewModel()
            val usuario = uiState.usuario
            PessoasConfiancaScreen(
                usuarioId = viewModel.obterUidAutenticado().orEmpty(),
                responsavelPadraoId = usuario?.responsavelPadraoId,
                viewModel = responsavelViewModel,
                onVoltar = { navController.popBackStack() }
            )
        }

        composable(Screen.Assinaturas.rota) {
            val assinaturaViewModel: AssinaturaViewModel = viewModel()
            AssinaturasScreen(
                usuarioId = viewModel.obterUidAutenticado().orEmpty(),
                viewModel = assinaturaViewModel,
                onVoltar = { navController.popBackStack() }
            )
        }

        composable(Screen.Medicamentos.rota) {
            val medicamentoViewModel: MedicamentoViewModel = viewModel()
            val uid = viewModel.obterUidAutenticado().orEmpty()
            MedicamentosListScreen(
                usuarioId = uid,
                viewModel = medicamentoViewModel,
                onVoltar = { navController.popBackStack() },
                onAdicionar = {
                    navController.navigate(
                        Screen.MedicamentoForm.criarRota(ScreenMedicamentoId.NOVO)
                    )
                },
                onEditar = { id ->
                    navController.navigate(Screen.MedicamentoForm.criarRota(id))
                }
            )
        }

        composable(
            route = Screen.MedicamentoForm.rota,
            arguments = listOf(
                navArgument("medicamentoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val parentEntry = navController.getBackStackEntry(Screen.Medicamentos.rota)
            val medicamentoViewModel: MedicamentoViewModel = viewModel(parentEntry)
            val medicamentoId = backStackEntry.arguments?.getString("medicamentoId")
                ?: ScreenMedicamentoId.NOVO
            val uid = viewModel.obterUidAutenticado().orEmpty()
            MedicamentoFormScreen(
                usuarioId = uid,
                medicamentoId = medicamentoId,
                viewModel = medicamentoViewModel,
                onVoltar = { navController.popBackStack() },
                onSalvo = { navController.popBackStack() }
            )
        }
    }
}
