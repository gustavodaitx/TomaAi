package br.com.tomai.ui.responsaveis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
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
    LaunchedEffect(usuarioId) { viewModel.carregar(usuarioId) }
    LaunchedEffect(state.mensagem) {
        if (state.mensagem != null) {
            nome = ""; email = ""; telefone = ""; aceitouAvisos = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Pessoas de confiança") }, navigationIcon = {
        OutlinedButton(onClick = onVoltar) { Text("Voltar") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(telefone, { telefone = it }, label = { Text("Telefone (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(aceitouAvisos, { aceitouAvisos = it })
                Text("Autorizou receber avisos sobre doses")
            }
            Button(onClick = {
                viewModel.salvar(usuarioId, nome, email, telefone, aceitouAvisos)
            }, enabled = !state.carregando) { Text("Cadastrar pessoa") }
            state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (state.carregando) CircularProgressIndicator()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.pessoas, key = { it.id }) { pessoa ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(pessoa.nome, style = MaterialTheme.typography.titleMedium)
                            Text(pessoa.email)
                            Text(if (pessoa.aceitouReceberAvisos) "Autorizou avisos" else "Sem autorização para avisos")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (responsavelPadraoId != pessoa.id) {
                                    OutlinedButton(onClick = { viewModel.definirPadrao(usuarioId, pessoa.id) }) { Text("Definir como padrão") }
                                } else Text("Responsável padrão", modifier = Modifier.padding(12.dp))
                                OutlinedButton(onClick = { viewModel.remover(usuarioId, pessoa.id) }) { Text("Remover") }
                            }
                        }
                    }
                }
            }
        }
    }
}
