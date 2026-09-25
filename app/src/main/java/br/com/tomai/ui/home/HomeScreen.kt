package br.com.tomai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import br.com.tomai.ui.components.AppButton
import br.com.tomai.ui.components.DoseCard
import br.com.tomai.viewmodel.AuthViewModel
import br.com.tomai.viewmodel.DoseViewModel
import br.com.tomai.viewmodel.AssinaturaViewModel

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
    val uiState by viewModel.uiState.collectAsState()
    val doseState by doseViewModel.uiState.collectAsState()
    val assinaturaState by assinaturaViewModel.uiState.collectAsState()
    val usuario = uiState.usuario
    val uidExibicao = uiState.firestoreUid
        ?: usuario?.id?.takeIf { it.isNotBlank() }
        ?: viewModel.obterUidAutenticado()
        ?: "N/D"

    LaunchedEffect(Unit) {
        val uidAtual = viewModel.obterUidAutenticado()
        if (!uidAtual.isNullOrBlank()) {
            viewModel.observarUsuarioFirestore(uidAtual)
        }
    }

    LaunchedEffect(usuario?.id) {
        val uid = usuario?.id
        if (!uid.isNullOrBlank() && uiState.firestoreUid.isNullOrBlank()) {
            viewModel.observarUsuarioFirestore(uid)
        }
    }

    LaunchedEffect(uidExibicao) {
        if (uidExibicao != "N/D") {
            doseViewModel.iniciar(uidExibicao)
            assinaturaViewModel.carregar(uidExibicao)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = "TomaAí",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TomaAí",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.logout(onConcluido = onLogoutConcluido)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sair",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val assinaturaAtual = assinaturaState.assinaturas.firstOrNull {
                it["status"]?.toString()?.uppercase() in setOf("ACTIVE", "ATIVA")
            } ?: assinaturaState.assinaturas.firstOrNull()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Minha assinatura", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    Text(assinaturaAtual?.get("planoId")?.toString()?.let { "Plano $it" } ?: "Nenhum plano ativo",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text(assinaturaAtual?.get("status")?.toString()?.replace('_', ' ') ?: "Consulte os planos disponíveis",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .88f))
                    AppButton("Ver assinatura", onClick = onNavegarAssinaturas,
                        containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary)
                }
            }
            // Card de Perfil do Usuário
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Foto do usuário",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = usuario?.nome?.ifBlank { "Usuário TomaAí" } ?: "Usuário TomaAí",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = usuario?.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Ativo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Conta Ativa • Perfil: ${usuario?.perfil ?: "PACIENTE"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Histórico de doses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Consulte doses anteriores por dia, semana ou mês.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Abrir histórico", onClick = onNavegarHistorico)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Planos e assinatura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Consulte planos, cobranças e gerencie sua assinatura pelo ambiente Sandbox.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Ver planos e pagamentos", onClick = onNavegarAssinaturas)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Pessoas de confiança", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Cadastre um responsável e escolha se ele autorizou receber avisos sobre doses.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Gerenciar responsáveis", onClick = onNavegarPessoasConfianca)
                }
            }

            // Status da Sessão e do Módulo 1
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Autenticação Segura",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Módulo 1 Concluído — Autenticação Ativa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "UID Firestore: $uidExibicao\nSessão sincronizada em tempo real com /usuarios/{uid}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Módulo 2 — Medicamentos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cadastre nome, dosagem, frequência, horários e estoque. Dados salvos em /medicamentos vinculados ao seu UID.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppButton(
                        text = "Meus Medicamentos",
                        onClick = onNavegarMedicamentos
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Módulo 3 — Doses de hoje",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Checklist diário: Tomado, Pular ou Atrasado. Lembretes locais nos horários cadastrados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppButton(
                        text = "Abrir checklist de hoje",
                        onClick = onNavegarDosesDia
                    )
                    if (doseState.isLoading && doseState.doses.isEmpty()) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.padding(top = 12.dp))
                    } else if (doseState.doses.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(doseState.doses, key = { it.id }) { dose ->
                                DoseCard(
                                    nome = dose.medicamentoNome,
                                    dosagem = dose.dosagem,
                                    horario = dose.horarioProgramado,
                                    status = when (dose.status) {
                                        br.com.tomai.model.Dose.STATUS_CONFIRMADA -> "CONFIRMADA"
                                        br.com.tomai.model.Dose.STATUS_NAO_CONFIRMADA -> "NÃO CONFIRMADA"
                                        br.com.tomai.model.Dose.STATUS_IGNORADA -> "IGNORADA"
                                        else -> "PENDENTE"
                                    },
                                    onConfirmarClick = { doseViewModel.marcarTomado(dose.id, dose.medicamentoId) },
                                    podeConfirmar = dose.status == br.com.tomai.model.Dose.STATUS_PENDENTE &&
                                        doseState.doseEmAtualizacao != dose.id
                                )
                            }
                        }
                    } else {
                        Text("Nenhuma dose cadastrada para hoje.", modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.TextButton(onClick = onNavegarHistorico) {
                        Text("Ver histórico de doses")
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Próximos Módulos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Módulo 4: Responsáveis de confiança\n• Módulo 5: Planos Asaas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AppButton(
                text = "Encerrar Sessão (Logout)",
                onClick = {
                    viewModel.logout(onConcluido = onLogoutConcluido)
                },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        }
    }
}
