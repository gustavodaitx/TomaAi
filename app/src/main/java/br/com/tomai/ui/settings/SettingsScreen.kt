package br.com.tomai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.tomai.BuildConfig
import br.com.tomai.data.repository.LimpezaDadosRepository
import br.com.tomai.model.Usuario
import br.com.tomai.notifications.DoseReminderScheduler
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(usuario: Usuario?, onVoltar: () -> Unit, onAbrirAreaDesenvolvimento: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mostrarConfirmacao by remember { mutableStateOf(false) }
    var confirmacaoDigitada by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<String?>(null) }
    var resultadoFalhou by remember { mutableStateOf(false) }
    val uid = usuario?.id.orEmpty()
    val podeLimpar = usuario?.perfil == Usuario.PERFIL_PACIENTE
    val nome = usuario?.nome?.trim().orEmpty()
    val identificacao = when (usuario?.perfil) {
        Usuario.PERFIL_PACIENTE -> "Paciente${nome.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}"
        Usuario.PERFIL_PESSOA_DE_CONFIANCA -> "Pessoa de confiança${nome.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}"
        else -> nome.ifBlank { "Perfil" }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Configurações") }, navigationIcon = {
        TextButton(onClick = onVoltar) { Text("Voltar") }
    }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(identificacao, style = MaterialTheme.typography.titleMedium)
                    Text(usuario?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text("Conta e perfil serão preservados pela limpeza de dados de medicação.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Limpar dados de medicação", style = MaterialTheme.typography.titleMedium)
                    Text("Remove medicamentos, horários, doses, histórico e alertas pertencentes a esta conta. Preserva o perfil, o plano, os pagamentos, a pessoa de confiança e os vínculos.")
                    Text("A exclusão é permanente. Alarmes salvos neste aparelho serão cancelados.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { confirmacaoDigitada = ""; mostrarConfirmacao = true }, enabled = !carregando && uid.isNotBlank() && podeLimpar) {
                        Text("Limpar dados de medicação")
                    }
                    if (!podeLimpar) Text("A limpeza de dados de medicação está disponível apenas no perfil de paciente.", style = MaterialTheme.typography.bodySmall)
                }
            }

            resultado?.let {
                Text(it, color = if (resultadoFalhou) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            if (carregando) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Limpando dados de medicação…")
            }

            if (BuildConfig.DEBUG) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Desenvolvimento", style = MaterialTheme.typography.titleMedium)
                        Text("Ferramentas de desenvolvimento são separadas das configurações de uso diário.")
                        OutlinedButton(onClick = onAbrirAreaDesenvolvimento) {
                            Text("Abrir ferramentas de desenvolvimento")
                        }
                    }
                }
            }
        }
    }

    if (mostrarConfirmacao) {
        AlertDialog(
            onDismissRequest = { if (!carregando) mostrarConfirmacao = false },
            title = { Text("Confirmar limpeza") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Serão removidos os medicamentos, horários, doses, histórico e alertas desta conta. A conta Firebase, o perfil, o plano, pagamentos, vínculos e cadastros de pessoas de confiança serão preservados. Registros apagados podem não ser recuperáveis.")
                    Text("Digite LIMPAR para confirmar.", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = confirmacaoDigitada,
                        onValueChange = { confirmacaoDigitada = it.uppercase().take(6) },
                        label = { Text("Confirmação") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmacaoDigitada == "LIMPAR" && !carregando,
                    onClick = {
                        mostrarConfirmacao = false
                        carregando = true
                        resultado = null
                          resultadoFalhou = false
                        scope.launch {
                            LimpezaDadosRepository().limparDadosMedicacao().fold(
                                onSuccess = { resposta ->
                                    DoseReminderScheduler.limparAgendamentos(context, uid)
                                    resultado = "Limpeza concluída. ${resposta.totalRemovido} registro(s) removido(s) nas coleções de medicação e alertas."
                                    resultadoFalhou = false
                                },
                                onFailure = { erro ->
                                    resultado = erro.localizedMessage ?: "A limpeza não foi concluída integralmente. Tente novamente."
                                    resultadoFalhou = true
                                }
                            )
                            carregando = false
                        }
                    }
                ) { Text("Apagar dados") }
            },
            dismissButton = { TextButton(onClick = { mostrarConfirmacao = false }, enabled = !carregando) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopmentToolsScreen(onVoltar: () -> Unit) {
    val projectId = remember { FirebaseApp.getInstance().options.projectId.orEmpty() }
    Scaffold(topBar = { TopAppBar(title = { Text("Ferramentas de desenvolvimento") }, navigationIcon = {
        TextButton(onClick = onVoltar) { Text("Voltar") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reset de ambiente de teste", style = MaterialTheme.typography.titleLarge)
            Text("Projeto Firebase configurado: $projectId")
            Text("O reset abrangente está desativado. Este aplicativo ainda usa uma única configuração Firebase para debug e release; habilitar exclusões aqui poderia apagar dados de produção.")
            Text("Para habilitar com segurança, configure um projeto Firebase exclusivo de teste e uma variante de build vinculada a esse projeto.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {}, enabled = false) { Text("Reset de ambiente de teste indisponível") }
        }
    }
}
