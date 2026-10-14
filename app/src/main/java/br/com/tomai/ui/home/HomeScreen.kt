package br.com.tomai.ui.home

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.tooling.preview.Preview
import br.com.tomai.ui.components.TomaAiLogo
import br.com.tomai.ui.components.DoseCard
import br.com.tomai.ui.theme.TomaAiTheme
import br.com.tomai.model.Dose
import br.com.tomai.viewmodel.AuthViewModel
import br.com.tomai.viewmodel.DoseViewModel
import br.com.tomai.notifications.DoseReminderScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AuthViewModel,
    doseViewModel: DoseViewModel,
    onNavegarMedicamentos: () -> Unit = {},
    onNavegarDosesDia: () -> Unit = {},
    onNavegarHistorico: () -> Unit = {},
    onNavegarPessoasConfianca: () -> Unit = {},
    onNavegarAssinaturas: () -> Unit = {},
    onLogoutConcluido: () -> Unit
) {
    val auth by viewModel.uiState.collectAsState()
    val doses by doseViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val nome = auth.usuario?.nome?.substringBefore(' ')?.ifBlank { "Olá" } ?: "Olá"
    val uid = auth.firestoreUid ?: auth.usuario?.id?.takeIf(String::isNotBlank)
        ?: viewModel.obterUidAutenticado().orEmpty()
    var codigoCopiado by remember { mutableStateOf(false) }
    var notificacoesDesativadas by remember(uid) {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var alarmesExatosIndisponiveis by remember(uid) {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !(context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager)
                    .canScheduleExactAlarms()
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificacoesDesativadas = !granted
        context.getSharedPreferences("permissoes_notificacao", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("solicitada", true).apply()
        if (!granted) android.util.Log.i("TOMAI_NOTIFICACOES", "Permissão de notificações recusada.")
    }

    LaunchedEffect(Unit) {
        viewModel.obterUidAutenticado()?.takeIf(String::isNotBlank)?.let(viewModel::observarUsuarioFirestore)
    }
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            doseViewModel.iniciar(uid)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED &&
                !context.getSharedPreferences("permissoes_notificacao", android.content.Context.MODE_PRIVATE)
                    .getBoolean("solicitada", false)
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(uid, doses.isLoading, doses.isSincronizandoAgenda, doses.erro, doses.doses,
        doses.medicamentoIdsValidosHoje, doses.medicamentosSincronizados, auth.usuario?.perfil,
        auth.usuario?.pacienteUid, auth.usuario?.aceitouReceberAvisos) {
        if (uid.isBlank() || doses.isLoading || doses.isSincronizandoAgenda || doses.erro != null) return@LaunchedEffect
        val perfil = auth.usuario?.perfil
        if (perfil != br.com.tomai.model.Usuario.PERFIL_PACIENTE &&
            perfil != br.com.tomai.model.Usuario.PERFIL_PESSOA_DE_CONFIANCA
        ) return@LaunchedEffect
        if (perfil == br.com.tomai.model.Usuario.PERFIL_PACIENTE &&
            doses.medicamentosSincronizados.any { it.usuarioId != uid }
        ) return@LaunchedEffect
        val podeReceberLembretes = perfil == br.com.tomai.model.Usuario.PERFIL_PACIENTE ||
            (perfil == br.com.tomai.model.Usuario.PERFIL_PESSOA_DE_CONFIANCA &&
                !auth.usuario?.pacienteUid.isNullOrBlank() && auth.usuario?.aceitouReceberAvisos == true)
        val pacienteUidAutorizado = if (perfil == br.com.tomai.model.Usuario.PERFIL_PACIENTE) {
            uid
        } else {
            auth.usuario?.pacienteUid
        }
        val dosesAutorizadas = if (podeReceberLembretes) {
            doses.doses.filter {
                it.medicamentoId in doses.medicamentoIdsValidosHoje &&
                    it.usuarioId == pacienteUidAutorizado
            }
        } else emptyList()
        val medicamentosAutorizados = if (podeReceberLembretes) {
            doses.medicamentosSincronizados.filter { it.usuarioId == pacienteUidAutorizado }
        } else emptyList()
        DoseReminderScheduler.sincronizar(context, uid, dosesAutorizadas, medicamentosAutorizados)
    }

    DisposableEffect(lifecycleOwner, uid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificacoesDesativadas = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (event == Lifecycle.Event.ON_RESUME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmesExatosIndisponiveis = !(context.getSystemService(android.content.Context.ALARM_SERVICE)
                    as AlarmManager).canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

            if (notificacoesDesativadas) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Ative as notificações para receber os lembretes de medicamentos neste aparelho.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = {
                            val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(settingsIntent)
                        }) {
                            Text("Abrir configurações de notificações")
                        }
                    }
                }
            }

            if (alarmesExatosIndisponiveis) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Sem permissão para alarmes exatos, o Android pode atrasar os lembretes.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = {
                            val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(settingsIntent)
                        }) {
                            Text("Permitir horários mais precisos")
                        }
                    }
                }
            }

            if (auth.usuario?.perfil == br.com.tomai.model.Usuario.PERFIL_PACIENTE) {
                val codigoPaciente = auth.usuario?.codigoPaciente ?: auth.usuario?.codigoVinculo
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Seu Código de Vínculo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (codigoPaciente.isNullOrBlank()) {
                            Text("Carregando seu código…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(codigoPaciente, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Compartilhe este código com sua Pessoa de Confiança.", style = MaterialTheme.typography.bodyMedium)
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(codigoPaciente))
                                    codigoCopiado = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (codigoCopiado) "Código copiado" else "Copiar código")
                            }
                        }
                    }
                }
            }
            if (auth.usuario?.perfil == br.com.tomai.model.Usuario.PERFIL_PESSOA_DE_CONFIANCA) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pessoa de Confiança", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = auth.usuario?.codigoPacienteDigitado?.let { "Código vinculado: $it" }
                                ?: "Conta vinculada ao paciente.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (auth.usuario?.aceitouReceberAvisos == true) "Avisos de doses ativados" else "Avisos de doses desativados",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
                        val planoAtual = auth.usuario?.planoAtual ?: "FREE"
                        val nomePlano = when (planoAtual) { "QUINZENAL" -> "Quinzenal"; "MENSAL" -> "Mensal"; else -> "Free" }
                        Text("Plano atual: $nomePlano", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    TomaAiTheme {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TomaAiLogo(size = 56.dp)
            Text("Doses de hoje", style = MaterialTheme.typography.titleLarge)
            DoseCard("Vitamina D", "1 comprimido", "08:00", Dose.STATUS_PENDENTE, onConfirmarClick = {})
        }
    }
}
