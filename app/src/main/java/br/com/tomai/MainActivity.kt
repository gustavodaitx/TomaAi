package br.com.tomai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import br.com.tomai.ui.navigation.AppNavGraph
import br.com.tomai.ui.theme.TomaAiTheme
import br.com.tomai.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TomaAiTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()
                AppNavGraph(
                    navController = navController,
                    viewModel = authViewModel
                )
            }
        }
    }
}
