package br.com.tomai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.tomai.ui.components.TomaAiLogo
import br.com.tomai.ui.components.DoseCard
import br.com.tomai.model.Dose
import br.com.tomai.viewmodel.AssinaturaViewModel
import br.com.tomai.viewmodel.AuthViewModel
import br.com.tomai.viewmodel.DoseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AuthViewModel,
    doseViewModel: DoseViewModel,
    assinaturaViewModel: AssinaturaViewModel,
    onNavegarMedicamentos: () -> Unit = {},
    onNavegarDosesDia: () -> Unit = {},
    onNavegarHistorico: () -> Unit = {},
    onNavegarPessoasConfianca: () -> Unit = {},
    onNavegarAssinaturas: () -> Unit = {},
    onLogoutConcluido: () -> Unit
) {
    val auth by viewModel.uiState.collectAsState()
    val doses by doseViewModel.uiState.collectAsState()
    val assinatura by assinaturaViewModel.uiState.collectAsState()
    val nome = auth.usuario?.nome?.substringBefore(' ')?.ifBlank { "Olá" } ?: "Olá"
    val uid = auth.firestoreUid ?: auth.usuario?.id?.takeIf(String::isNotBlank)
        ?: viewModel.obterUidAutenticado().orEmpty()

    LaunchedEffect(Unit) {
        viewModel.obterUidAutenticado()?.takeIf(String::isNotBlank)?.let(viewModel::observarUsuarioFirestore)
    }
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            doseViewModel.iniciar(uid)
            assinaturaViewModel.carregar(uid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TomaAiLogo(size = 48.dp) },
                actions = {
                    IconButton(onClick = { viewModel.logout(onConcluido = onLogoutConcluido) }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sair")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { insets ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(insets).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Olá, $nome!", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Acompanhe sua rotina e as confirmações de doses de hoje.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onNavegarAssinaturas),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Plano de Assinatura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val atual = assinatura.assinaturas.firstOrNull { it["status"]?.toString()?.uppercase() in setOf("ATIVA", "ACTIVE") }
                            ?: assinatura.assinaturas.firstOrNull()
                        Text(atual?.get("planoId")?.toString()?.replace('_', ' ') ?: "Conheça os planos disponíveis",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Text("Ações Rápidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Medicamentos", Icons.Default.Medication, onNavegarMedicamentos, Modifier.weight(1f))
                QuickAction("Doses", Icons.Default.AccessTime, onNavegarDosesDia, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Responsáveis", Icons.Default.Group, onNavegarPessoasConfianca, Modifier.weight(1f))
                QuickAction("Planos", Icons.Default.Star, onNavegarAssinaturas, Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Resumo de Doses de Hoje", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        doses.isLoading && doses.doses.isEmpty() -> "Carregando sua agenda de hoje…"
                        doses.doses.isEmpty() -> "Nenhuma dose agendada para hoje. Cadastre um medicamento!"
                        else -> "${doses.pendentes} pendente(s) · ${doses.concluidas} concluída(s) de ${doses.doses.size} dose(s)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            doses.doses.take(3).forEach { dose ->
                DoseCard(
                    nome = dose.medicamentoNome,
                    dosagem = dose.dosagem,
                    horario = dose.horarioProgramado,
                    status = dose.status,
                    onConfirmarClick = { doseViewModel.marcarTomado(dose.id, dose.medicamentoId) },
                    podeConfirmar = dose.status == Dose.STATUS_PENDENTE
                )
            }
            androidx.compose.material3.Button(onClick = onNavegarHistorico, modifier = Modifier.fillMaxWidth()) {
                Text("Ver Histórico Completo de Doses")
            }
            TextButton(onClick = onNavegarDosesDia, modifier = Modifier.fillMaxWidth()) { Text("Abrir doses de hoje") }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun QuickAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(88.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
