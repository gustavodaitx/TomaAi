package br.com.tomai.ui.assinaturas

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import br.com.tomai.viewmodel.AssinaturaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanosEAssinaturaScreen(usuarioId: String, viewModel: AssinaturaViewModel, onVoltar: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var planoSelecionado by remember { mutableStateOf<br.com.tomai.model.Plano?>(null) }
    var metodo by remember { mutableStateOf<String?>(null) }
    val plano = planoSelecionado
    Scaffold(topBar = { TopAppBar(title = { Text(when (metodo) { "PIX" -> "Pix de teste"; "CARTAO" -> "Cartão de teste"; else -> "Planos de apoio" }) }, navigationIcon = { OutlinedButton(onClick = { if (metodo != null) metodo = null else if (plano != null) planoSelecionado = null else onVoltar() }) { Text("Voltar") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (plano == null) {
                item { Text("Todos os planos mantêm acesso integral ao aplicativo.", style = MaterialTheme.typography.bodyLarge) }
                items(state.planos, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Plano ${item.nome}", style = MaterialTheme.typography.titleLarge)
                            Text(if (item.id == "FREE") "R$ 0,00 · sem cobrança" else "${formatarValor(item.valor)} · ${item.ciclo}")
                            Text(item.descricao)
                            Button(onClick = {
                                if (item.id == "FREE") viewModel.simularSelecao(usuarioId, "FREE") else planoSelecionado = item
                            }, enabled = !state.carregando) { Text(if (item.id == "FREE") "Selecionar Free" else "Continuar") }
                        }
                    }
                }
            } else if (metodo == null) {
                item {
                    Text("${plano.nome}: ${formatarValor(plano.valor)} · ${plano.ciclo}", style = MaterialTheme.typography.titleLarge)
                    Text("Apoio voluntário ao projeto. Esta demonstração não realiza cobranças.")
                    Button(onClick = { metodo = "PIX" }, modifier = Modifier.fillMaxWidth()) { Text("Pix · simulação") }
                    OutlinedButton(onClick = { metodo = "CARTAO" }, modifier = Modifier.fillMaxWidth()) { Text("Cartão de crédito · simulação") }
                }
            } else if (metodo == "PIX") {
                item {
                    Text("Pagamento fictício: ${plano.nome} · ${formatarValor(plano.valor)}", style = MaterialTheme.typography.titleLarge)
                    Text("SIMULAçãO DE TESTE · QR visual demonstrativo, sem chave Pix e sem transferência bancária.")
                    FauxQrCode("TOMAAI-TESTE-PIX-${plano.id}-${if (plano.id == "QUINZENAL") "1490" else "2490"}", Modifier.padding(vertical = 12.dp).size(200.dp))
                    Text("Identificador fictício: TOMAAI-TESTE-PIX-${plano.id}-${if (plano.id == "QUINZENAL") "1490" else "2490"}")
                    Button(onClick = { viewModel.simularSelecao(usuarioId, plano.id, "PIX"); metodo = null; planoSelecionado = null }, enabled = !state.carregando) { Text("Simular pagamento aprovado") }
                    OutlinedButton(onClick = { metodo = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                }
            } else {
                item {
                    Text("Pagamento demonstrativo: ${plano.nome} · ${formatarValor(plano.valor)}", style = MaterialTheme.typography.titleLarge)
                    Text("Ambiente de teste. Não informe dados de um cartão real.", color = MaterialTheme.colorScheme.error)
                    Text("Cartão fictício predefinido: 0000 0000 0000 0000 · validade 12/30 · CVV 000")
                    Text("Os dados acima são exemplos fixos e não são enviados, armazenados ou registrados.")
                    Button(onClick = { viewModel.simularSelecao(usuarioId, plano.id, "CARTAO"); metodo = null; planoSelecionado = null }, enabled = !state.carregando) { Text("Simular pagamento aprovado") }
                    OutlinedButton(onClick = { metodo = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                }
            }
            state.erro?.let { erro -> item { Text(erro, color = MaterialTheme.colorScheme.error) } }
            state.mensagem?.let { mensagem -> item { Text(mensagem, color = MaterialTheme.colorScheme.primary) } }
            if (state.carregando) item { Text("Salvando...") }
        }
    }
}

@Composable
private fun FauxQrCode(payload: String, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) {
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 256, 256)
        val image = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.RGB_565)
        for (y in 0 until 256) for (x in 0 until 256) image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        image.asImageBitmap()
    }
    androidx.compose.foundation.Image(bitmap = bitmap, contentDescription = "QR Code de teste contendo apenas o identificador fictício", modifier = modifier)
}
private fun formatarValor(valor: Double): String =
    java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR")).format(valor)
