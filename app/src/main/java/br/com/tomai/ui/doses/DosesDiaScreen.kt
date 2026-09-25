package br.com.tomai.ui.doses

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.tomai.model.Dose
import br.com.tomai.notifications.DoseNotificationHelper
import br.com.tomai.notifications.DoseReminderScheduler
import br.com.tomai.viewmodel.DoseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DosesDiaScreen(
    usuarioId: String,
    viewModel: DoseViewModel,
    onVoltar: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(usuarioId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        DoseNotificationHelper.garantirCanal(context)
        viewModel.iniciar(usuarioId)
    }

    LaunchedEffect(uiState.doses) {
        DoseReminderScheduler.reagendarPendentes(context, uiState.doses)
    }

    LaunchedEffect(uiState.erro, uiState.sucessoMensagem) {
        uiState.erro?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limparMensagens()
        }
        uiState.sucessoMensagem?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limparMensagens()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Doses de hoje", fontWeight = FontWeight.Bold)
                        Text(
                            text = uiState.dataAgenda,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.doses.isNotEmpty()) {
                val progresso = uiState.concluidas.toFloat() / uiState.doses.size.toFloat()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "${uiState.concluidas}/${uiState.doses.size} registradas",
                        style = MaterialTheme.typography.labelLarge
                    )
                    LinearProgressIndicator(
                        progress = { progresso },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }

            if (uiState.isSincronizandoAgenda) {
                Text(
                    text = "Sincronizando agenda…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            when {
                uiState.isLoading && uiState.doses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.doses.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nenhuma dose para hoje",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Cadastre medicamentos com horários para gerar a agenda diária.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.doses, key = { it.id }) { dose ->
                            DoseChecklistCard(
                                dose = dose,
                                emAtualizacao = uiState.doseEmAtualizacao == dose.id,
                                onTomado = {
                                    viewModel.marcarTomado(dose.id, dose.medicamentoId)
                                },
                                onPular = {
                                    viewModel.marcarPular(dose.id, dose.medicamentoId)
                                },
                                onAtrasado = {
                                    viewModel.marcarAtrasado(dose.id, dose.medicamentoId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseChecklistCard(
    dose: Dose,
    emAtualizacao: Boolean,
    onTomado: () -> Unit,
    onPular: () -> Unit,
    onAtrasado: () -> Unit
) {
    val atrasada = dose.estaAtrasada()
    val statusLabel = when (dose.status) {
        Dose.STATUS_CONFIRMADA -> "Tomado"
        Dose.STATUS_IGNORADA -> "Pulado"
        Dose.STATUS_NAO_CONFIRMADA -> "Não confirmado"
        else -> if (atrasada) "Atrasado (pendente)" else "Pendente"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (dose.status) {
                Dose.STATUS_CONFIRMADA -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                Dose.STATUS_IGNORADA -> MaterialTheme.colorScheme.surfaceVariant
                Dose.STATUS_NAO_CONFIRMADA -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                else -> if (atrasada) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dose.medicamentoNome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dose.dosagem,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = dose.horarioProgramado,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = "Status: $statusLabel",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (dose.status == Dose.STATUS_PENDENTE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTomado,
                        enabled = !emAtualizacao,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text("Tomado", modifier = Modifier.padding(start = 4.dp))
                    }
                    OutlinedButton(
                        onClick = onPular,
                        enabled = !emAtualizacao,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Text("Pular", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                OutlinedButton(
                    onClick = onAtrasado,
                    enabled = !emAtualizacao,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text("Marcar atrasado / não confirmado", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
