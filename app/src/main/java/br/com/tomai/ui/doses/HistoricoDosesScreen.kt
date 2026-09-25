package br.com.tomai.ui.doses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.tomai.model.Dose
import br.com.tomai.ui.components.StatusChip
import br.com.tomai.viewmodel.DoseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PeriodoHistorico(val titulo: String, val dias: Long) {
    HOJE("Hoje", 0),
    SEMANA("Semana", 6),
    MES("Mês", 29)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoDosesScreen(usuarioId: String, viewModel: DoseViewModel, onVoltar: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var periodo by remember { mutableStateOf(PeriodoHistorico.HOJE) }
    var medicamentoFiltro by remember { mutableStateOf("Todos") }
    var menuMedicamentosAberto by remember { mutableStateOf(false) }
    var doseSelecionada by remember { mutableStateOf<Dose?>(null) }
    val hoje = LocalDate.now()
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    LaunchedEffect(usuarioId, periodo) {
        val inicio = hoje.minusDays(periodo.dias).format(formatter)
        viewModel.carregarHistorico(usuarioId, inicio, hoje.format(formatter))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (doseSelecionada == null) "Histórico de doses" else "Detalhes da dose", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (doseSelecionada != null) doseSelecionada = null else onVoltar() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val selecionada = doseSelecionada
        if (selecionada != null) {
            DoseDetalhe(selecionada, Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                doseSelecionada = null
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text("Histórico de doses", Modifier.padding(start = 20.dp, top = 18.dp),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeriodoHistorico.entries.forEach { opcao ->
                        FilterChip(selected = periodo == opcao, onClick = { periodo = opcao }, label = { Text(opcao.titulo) })
                    }
                }
                val medicamentos = listOf("Todos") + state.historico.map { it.medicamentoNome }.distinct().sorted()
                ExposedDropdownMenuBox(expanded = menuMedicamentosAberto,
                    onExpandedChange = { menuMedicamentosAberto = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    OutlinedTextField(value = medicamentoFiltro, onValueChange = {}, readOnly = true,
                        label = { Text("Filtrar por medicamento") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuMedicamentosAberto) },
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = menuMedicamentosAberto, onDismissRequest = { menuMedicamentosAberto = false }) {
                        medicamentos.forEach { nome -> DropdownMenuItem(text = { Text(nome) }, onClick = {
                            medicamentoFiltro = nome
                            menuMedicamentosAberto = false
                        }) }
                    }
                }
                Text("${periodo.titulo} · ${hoje.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall)
                when {
                    state.carregandoHistorico -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                    state.erroHistorico != null -> Text(state.erroHistorico.orEmpty(),
                        Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                    state.historico.isEmpty() -> Text("Nenhuma dose encontrada neste período.",
                        Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.historico.filter { medicamentoFiltro == "Todos" || it.medicamentoNome == medicamentoFiltro }, key = { it.id }) { dose ->
                            Card(onClick = { doseSelecionada = dose }, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(dose.medicamentoNome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${dose.dataAgenda.formatData()} · ${dose.horarioProgramado}",
                                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StatusChip(dose.status.rotuloStatus())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseDetalhe(dose: Dose, modifier: Modifier, onRetornar: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(dose.medicamentoNome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(dose.dosagem, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                DetailLine("Data", dose.dataAgenda.formatData())
                DetailLine("Horário previsto", dose.horarioProgramado)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status")
                    StatusChip(dose.status.rotuloStatus())
                }
                dose.confirmadaEm?.let {
                    val hora = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate())
                    DetailLine("Confirmada às", hora)
                }
            }
        }
        OutlinedButton(onClick = onRetornar, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao histórico") }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun String.formatData(): String = runCatching {
    LocalDate.parse(this).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(this)

private fun String.rotuloStatus(): String = when (this) {
    Dose.STATUS_CONFIRMADA -> "Tomada"
    Dose.STATUS_IGNORADA -> "Ignorada"
    Dose.STATUS_NAO_CONFIRMADA -> "Não confirmada"
    else -> "Pendente"
}
