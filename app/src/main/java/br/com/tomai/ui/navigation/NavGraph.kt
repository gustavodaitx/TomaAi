package br.com.tomai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import br.com.tomai.ui.cadastro.CadastroScreen
import br.com.tomai.ui.home.HomeScreen
import br.com.tomai.ui.login.LoginScreen
import br.com.tomai.ui.login.RecuperarSenhaScreen
import br.com.tomai.viewmodel.AuthViewModel

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
            HomeScreen(
                viewModel = viewModel,
                onLogoutConcluido = {
                    navController.navigate(Screen.Login.rota) {
                        popUpTo(Screen.Home.rota) { inclusive = true }
                    }
                }
            )
        }
    }
}
