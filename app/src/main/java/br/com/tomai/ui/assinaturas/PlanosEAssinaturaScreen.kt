package br.com.tomai.ui.assinaturas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.tomai.model.Plano
import br.com.tomai.viewmodel.AssinaturaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val planosDemonstracao = listOf(
    Plano(id = "QUINZENAL", nome = "Quinzenal", descricao = "Plano demonstrativo de 15 dias", valor = 19.90, ciclo = "QUINZENAL", diasRecorrencia = 15),
    Plano(id = "MENSAL", nome = "Mensal", descricao = "Plano demonstrativo de 30 dias", valor = 34.90, ciclo = "MENSAL", diasRecorrencia = 30)
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun PlanosEAssinaturaScreen(usuarioId: String, viewModel: AssinaturaViewModel, onVoltar: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val contratacoes = remember { mutableStateListOf<Plano>() }
    var formaPagamento by remember { mutableStateOf("PIX") }
    var planoPix by remember { mutableStateOf<Plano?>(null) }
    var planoCartao by remember { mutableStateOf<Plano?>(null) }
    var numeroCartao by remember { mutableStateOf("") }
    var nomeTitular by remember { mutableStateOf("") }
    var validade by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var erroFormulario by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }

    val pixCopiaCola = planoPix?.let { "PIX-DEMO-TOMAAI-${it.id}-ACADEMICO-NAO-PAGAVEL" }

    planoPix?.let { plano ->
        AlertDialog(
            onDismissRequest = { planoPix = null },
            title = { Text("Pix demonstrativo") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FakeQrCode(plano.id, Modifier.size(200.dp))
                    Text("QR Code fictício para demonstração. Não realize pagamentos.", style = MaterialTheme.typography.bodySmall)
                    Text("Pix copia e cola", style = MaterialTheme.typography.titleSmall)
                    Text(pixCopiaCola.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(pixCopiaCola.orEmpty())) }) { Text("Copiar chave") }
                    Button(onClick = {
                        contratacoes.add(plano)
                        mensagem = "Assinatura realizada com sucesso! (simulação)"
                        planoPix = null
                    }) { Text("Simular pagamento aprovado") }
                }
            },
            dismissButton = {
                TextButton(onClick = { planoPix = null }) { Text("Fechar") }
            }
        )
    }

    planoCartao?.let { plano ->
        ModalBottomSheet(onDismissRequest = { if (!carregando) planoCartao = null }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Cartão de crédito (demonstração)", style = MaterialTheme.typography.headlineSmall)
                Text("Nenhum dado será enviado ou salvo. Use informações fictícias.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = numeroCartao,
                    onValueChange = { numeroCartao = it.filter(Char::isDigit).take(19) },
                    label = { Text("Número do cartão") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = nomeTitular,
                    onValueChange = { nomeTitular = it },
                    label = { Text("Nome do titular") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = validade,
                    onValueChange = { validade = it.take(5) },
                    label = { Text("Validade (Mês/Ano)") },
                    placeholder = { Text("MM/AA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = cvv,
                    onValueChange = { cvv = it.filter(Char::isDigit).take(4) },
                    label = { Text("CVV") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                erroFormulario?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        if (numeroCartao.isBlank() || nomeTitular.isBlank() || validade.isBlank() || cvv.isBlank()) {
                            erroFormulario = "Preencha todos os campos para continuar."
                        } else {
                            erroFormulario = null
                            carregando = true
                            scope.launch {
                                delay(900)
                                contratacoes.add(plano)
                                mensagem = "Assinatura realizada com sucesso! (simulação)"
                                carregando = false
                                planoCartao = null
                                numeroCartao = ""
                                nomeTitular = ""
                                validade = ""
                                cvv = ""
                            }
                        }
                    },
                    enabled = !carregando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (carregando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Confirmar pagamento")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Planos e pagamentos") }, navigationIcon = {
            OutlinedButton(onClick = onVoltar) { Text("Voltar") }
        })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Planos", style = MaterialTheme.typography.headlineSmall)
                Text("Modo demonstração: pagamentos apenas simulados localmente.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = formaPagamento == "PIX", onClick = { formaPagamento = "PIX" })
                    Text("Pix")
                    RadioButton(selected = formaPagamento == "CREDIT_CARD", onClick = { formaPagamento = "CREDIT_CARD" })
                    Text("Cartão")
                }
                mensagem?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall) }
            }
            items(planosDemonstracao, key = { it.id }) { plano ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(plano.nome, style = MaterialTheme.typography.titleMedium)
                        Text(plano.descricao, style = MaterialTheme.typography.bodyMedium)
                        Text("${plano.diasRecorrencia} dias")
                        Text("R$ %.2f".format(plano.valor))
                        Button(onClick = {
                            mensagem = null
                            if (formaPagamento == "PIX") planoPix = plano
                            else {
                                erroFormulario = null
                                planoCartao = plano
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Selecionar e contratar") }
                    }
                }
            }
            item { Text("Assinaturas desta demonstração", style = MaterialTheme.typography.headlineSmall) }
            if (contratacoes.isEmpty()) {
                item { Text("Nenhuma assinatura simulada ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(contratacoes) { plano ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(plano.nome, style = MaterialTheme.typography.titleMedium)
                        Text("ATIVA • Demonstração local", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FakeQrCode(seed: String, modifier: Modifier = Modifier) {
    val modules = 25
    val random = remember(seed) { Random(seed.hashCode()) }
    val grid = remember(seed) { Array(modules) { BooleanArray(modules) { random.nextBoolean() } } }
    Canvas(modifier) {
        val cell = size.width / modules
        drawRect(Color.White)
        for (y in 0 until modules) for (x in 0 until modules) {
            val inFinderArea = (x < 8 && y < 8) || (x >= modules - 8 && y < 8) || (x < 8 && y >= modules - 8)
            val dark = if (inFinderArea) finderModule(x, y, modules) else grid[y][x]
            if (dark) drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell), size = androidx.compose.ui.geometry.Size(cell * 0.92f, cell * 0.92f))
        }
    }
}

private fun finderModule(x: Int, y: Int, modules: Int): Boolean {
    val originX = if (x >= modules - 8) modules - 8 else 0
    val originY = if (y >= modules - 8) modules - 8 else 0
    val localX = x - originX
    val localY = y - originY
    if (localX !in 0..6 || localY !in 0..6) return false
    return localX == 0 || localX == 6 || localY == 0 || localY == 6 ||
        (localX in 2..4 && localY in 2..4)
}
