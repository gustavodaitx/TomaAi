package br.com.tomai.ui.responsaveis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.tomai.ui.components.TomaAiButton
import br.com.tomai.viewmodel.PessoaConfiancaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PessoasConfiancaScreen(
    usuarioId: String,
    responsavelPadraoId: String?,
    viewModel: PessoaConfiancaViewModel,
    onVoltar: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var aceitouAvisos by remember { mutableStateOf(false) }
    var mostrarFormulario by remember { mutableStateOf(false) }
    var mostrarSucesso by remember { mutableStateOf(false) }
    var nomeSalvo by remember { mutableStateOf("") }
    var telefoneSalvo by remember { mutableStateOf("") }
    var emailSalvo by remember { mutableStateOf("") }
    var pessoaEmEdicao by remember { mutableStateOf<br.com.tomai.model.PessoaConfianca?>(null) }

    LaunchedEffect(usuarioId) { viewModel.carregar(usuarioId) }
    LaunchedEffect(state.mensagem) {
        val mensagem = state.mensagem.orEmpty()
        if (mensagem.contains("cadastrada", ignoreCase = true) || mensagem.contains("atualizada", ignoreCase = true)) {
            mostrarFormulario = false
            mostrarSucesso = true
            nome = ""
            email = ""
            telefone = ""
            aceitouAvisos = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Perfil", fontWeight = FontWeight.Bold)
                        Text("Pessoas de confiança", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (mostrarSucesso || mostrarFormulario) {
                        { mostrarSucesso = false; mostrarFormulario = false }
                    } else onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            mostrarSucesso -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Dados salvos!", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Card(Modifier.fillMaxWidth().padding(top = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(nomeSalvo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(emailSalvo, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(telefoneSalvo.ifBlank { "Contato salvo com segurança" },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("O envio de avisos depende de um canal de notificação configurado.",
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    TomaAiButton("Voltar ao perfil", onClick = { mostrarSucesso = false })
                }
            }
            mostrarFormulario -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(if (pessoaEmEdicao == null) "Adicionar pessoa de confiança" else "Editar pessoa de confiança", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(nome, { nome = it }, label = { Text("Nome") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(email, { email = it }, label = { Text("E-mail") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                            OutlinedTextField(telefone, { telefone = it }, label = { Text("Telefone (opcional)") },
                                placeholder = { Text("Ex.: (51) 99999-9999") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Receber aviso quando uma dose não for confirmada",
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = aceitouAvisos, onCheckedChange = { aceitouAvisos = it })
                            }
                            state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            TomaAiButton(if (pessoaEmEdicao == null) "Salvar" else "Salvar alterações", onClick = {
                                nomeSalvo = nome.trim()
                                telefoneSalvo = telefone.trim()
                                emailSalvo = email.trim()
                                viewModel.salvar(usuarioId, nome, email, telefone, aceitouAvisos, pessoaEmEdicao?.id.orEmpty())
                            }, isLoading = state.carregando)
                        }
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Column(Modifier.weight(1f)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text("Pessoas de confiança", style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold)
                                Text("Escolha quem pode receber avisos sobre doses não confirmadas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                                state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (state.carregando && state.pessoas.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                                if (!state.carregando && state.pessoas.isEmpty()) {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(22.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                            Text("Cadastre alguém para receber um aviso quando uma dose não for confirmada.",
                                                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                            items(state.pessoas, key = { it.id }) { pessoa ->
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(pessoa.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        if (pessoa.telefone.isNotBlank()) Text(pessoa.telefone, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(pessoa.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (pessoa.aceitouReceberAvisos) "Recebe avisos de doses" else "Avisos desativados",
                                            style = MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick = {
                                            pessoaEmEdicao = pessoa
                                            nome = pessoa.nome
                                            email = pessoa.email
                                            telefone = pessoa.telefone
                                            aceitouAvisos = pessoa.aceitouReceberAvisos
                                            mostrarFormulario = true
                                        }, enabled = !state.carregando) { Text("Editar") }
                                        if (responsavelPadraoId == pessoa.id) {
                                            Text("Responsável padrão", color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        } else {
                                            OutlinedButton(onClick = { viewModel.definirPadrao(usuarioId, pessoa.id) },
                                                enabled = !state.carregando) { Text("Definir como padrão") }
                                        }
                                        OutlinedButton(onClick = { viewModel.remover(usuarioId, pessoa.id) },
                                            enabled = !state.carregando) { Text("Remover pessoa") }
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = {
                        pessoaEmEdicao = null
                        nome = ""; email = ""; telefone = ""; aceitouAvisos = false
                        mostrarFormulario = true
                    },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        enabled = !state.carregando) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Text("Adicionar pessoa", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
