package br.com.tomai.ui.medicamentos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.window.Dialog
import br.com.tomai.model.Medicamento
import br.com.tomai.ui.components.AppButton
import br.com.tomai.ui.components.AppTextField
import br.com.tomai.viewmodel.MedicamentoViewModel
import br.com.tomai.viewmodel.ScreenMedicamentoId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicamentoFormScreen(
    usuarioId: String,
    medicamentoId: String,
    viewModel: MedicamentoViewModel,
    onVoltar: () -> Unit,
    onSalvo: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEdicao = medicamentoId.isNotBlank() && medicamentoId != ScreenMedicamentoId.NOVO
    var exibirTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(usuarioId) {
        if (usuarioId.isNotBlank()) {
            viewModel.iniciarObservacao(usuarioId)
        }
    }

    LaunchedEffect(medicamentoId) {
        if (isEdicao) {
            viewModel.carregarParaEdicao(medicamentoId)
        } else {
            viewModel.prepararNovoMedicamento()
        }
    }

    LaunchedEffect(uiState.erro) {
        uiState.erro?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limparMensagens()
        }
    }

    if (exibirTimePicker) {
        HorarioPickerDialog(
            onDismiss = { exibirTimePicker = false },
            onConfirm = { hora ->
                viewModel.adicionarHorario(hora)
                exibirTimePicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdicao) "Editar medicamento" else "Novo medicamento",
                        fontWeight = FontWeight.Bold
                    )
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
        if (uiState.isLoadingFormulario) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppTextField(
                value = uiState.nome,
                onValueChange = viewModel::atualizarNome,
                label = "Nome do medicamento",
                placeholder = "Ex: Paracetamol"
            )

            AppTextField(
                value = uiState.dosagem,
                onValueChange = viewModel::atualizarDosagem,
                label = "Dosagem",
                placeholder = "Ex: 500mg — 1 comprimido"
            )

            Text(
                text = "Frequência",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Medicamento.FREQUENCIAS_DISPONIVEIS.forEach { opcao ->
                    FilterChip(
                        selected = uiState.frequencia == opcao,
                        onClick = { viewModel.atualizarFrequencia(opcao) },
                        label = {
                            Text(
                                opcao.lowercase(Locale.getDefault())
                                    .replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }
                            )
                        }
                    )
                }
            }

            if (uiState.frequencia == Medicamento.FREQUENCIA_PERSONALIZADA) {
                Text("Dias da semana", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom").forEachIndexed { index, label ->
                        FilterChip(selected = index + 1 in uiState.diasSemana,
                            onClick = { viewModel.alternarDiaSemana(index + 1) }, label = { Text(label) })
                    }
                }
            }
            OutlinedTextField(value = uiState.dataInicio, onValueChange = viewModel::atualizarDataInicio,
                label = { Text("Data de início (opcional)") }, placeholder = { Text("AAAA-MM-DD") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = uiState.dataFim, onValueChange = viewModel::atualizarDataFim,
                label = { Text("Data de término (opcional)") }, placeholder = { Text("AAAA-MM-DD") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            if (!isEdicao) {
                AppTextField(
                    value = uiState.estoqueInicial,
                    onValueChange = viewModel::atualizarEstoqueInicial,
                    label = "Estoque inicial (unidades)",
                    placeholder = "Ex: 30"
                )
            } else {
                Text(
                    text = "Estoque atual: ${uiState.medicamentoEmEdicao?.estoqueAtual ?: 0} un.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Horários",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.horarios.forEach { horario ->
                    AssistChip(
                        onClick = { viewModel.removerHorario(horario.hora) },
                        label = { Text(horario.hora) },
                        leadingIcon = {
                            Icon(Icons.Default.AccessTime, contentDescription = null)
                        }
                    )
                }
            }

            AppButton(
                text = "Adicionar horário",
                onClick = { exibirTimePicker = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            AppButton(
                text = if (isEdicao) "Salvar alterações" else "Cadastrar medicamento",
                onClick = { viewModel.salvar(onSucesso = onSalvo) },
                isLoading = uiState.isLoadingSalvar
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorarioPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val state = rememberTimePickerState(is24Hour = true)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Selecione o horário",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            AppButton(
                text = "Confirmar",
                onClick = {
                    val hora = "%02d:%02d".format(state.hour, state.minute)
                    onConfirm(hora)
                },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
