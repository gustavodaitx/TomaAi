package br.com.tomai.ui.assinaturas

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import br.com.tomai.viewmodel.AssinaturaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssinaturasScreen(usuarioId: String, viewModel: AssinaturaViewModel, onVoltar: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var formaPagamento by remember { mutableStateOf("PIX") }
    LaunchedEffect(usuarioId) { viewModel.carregar(usuarioId) }
    Scaffold(topBar = { TopAppBar(title = { Text("Planos e pagamentos") }, navigationIcon = {
        OutlinedButton(onClick = onVoltar) { Text("Voltar") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Planos", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(formaPagamento == "PIX", { formaPagamento = "PIX" }); Text("Pix")
                    RadioButton(formaPagamento == "CREDIT_CARD", { formaPagamento = "CREDIT_CARD" }); Text("Cartão")
                }
                if (state.carregando) CircularProgressIndicator()
                state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            items(state.planos, key = { it["id"].toString() }) { plano ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(plano["nome"]?.toString() ?: plano["id"].toString(), style = MaterialTheme.typography.titleMedium)
                        val valor = (plano["valor"] as? Number)?.toDouble()
                        if (valor != null) Text("R$ %.2f".format(valor))
                        Button(onClick = { viewModel.contratar(plano["id"].toString(), formaPagamento) }, enabled = !state.carregando) {
                            Text("Contratar no Sandbox")
                        }
                    }
                }
            }
            item { Text("Assinaturas", style = MaterialTheme.typography.headlineSmall) }
            items(state.assinaturas, key = { it["documentId"].toString() }) { assinatura ->
                val id = assinatura["documentId"]?.toString().orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Plano ${assinatura["planoId"] ?: ""} • ${assinatura["status"] ?: "PENDENTE"}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.atualizarCobrancas(id) }) { Text("Atualizar cobranças") }
                            OutlinedButton(onClick = { viewModel.cancelar(id) }, enabled = assinatura["status"] != "CANCELADA") { Text("Cancelar") }
                        }
                    }
                }
            }
            item { Text("Cobranças", style = MaterialTheme.typography.headlineSmall) }
            state.pixPayload?.let { payload ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Pix copia e cola", style = MaterialTheme.typography.titleMedium)
                            Text(payload, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { clipboard.setText(AnnotatedString(payload)) }) { Text("Copiar código Pix") }
                        }
                    }
                }
            }
            items(state.cobrancas, key = { it["asaasPaymentId"]?.toString() ?: it.hashCode().toString() }) { cobranca ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${cobranca["status"] ?: "PENDENTE"} • ${cobranca["formaPagamento"] ?: ""}")
                        Text("Vencimento: ${cobranca["vencimento"] ?: "—"}")
                        if (cobranca["formaPagamento"] == "PIX") {
                            OutlinedButton(onClick = { viewModel.obterPix(cobranca["documentId"].toString()) }) { Text("Exibir Pix copia e cola") }
                        }
                        val invoiceUrl = cobranca["invoiceUrl"] as? String
                        if (!invoiceUrl.isNullOrBlank()) {
                            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                            TextButton(onClick = { uriHandler.openUri(invoiceUrl) }) { Text("Abrir página segura de pagamento") }
                        }
                    }
                }
            }
        }
    }
}
