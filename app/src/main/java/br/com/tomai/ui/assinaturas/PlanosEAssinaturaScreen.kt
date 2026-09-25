package br.com.tomai.ui.assinaturas

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import br.com.tomai.data.repository.DadosCartaoContratacao
import br.com.tomai.ui.components.StatusChip
import br.com.tomai.viewmodel.AssinaturaViewModel
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanosEAssinaturaScreen(usuarioId: String, viewModel: AssinaturaViewModel, onVoltar: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var formaPagamento by remember { mutableStateOf("PIX") }
    var planoCartaoId by remember { mutableStateOf<String?>(null) }
    var nome by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var validade by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var cep by remember { mutableStateOf("") }
    var numeroEndereco by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var erroCartao by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(usuarioId) { viewModel.carregar(usuarioId) }

    if (state.pixPayload != null) {
        PixDialog(state.pixEncodedImage, state.pixPayload!!, viewModel::limparPixPayload) {
            clipboard.setText(AnnotatedString(state.pixPayload.orEmpty()))
        }
    }
    if (planoCartaoId != null) {
        ModalBottomSheet(onDismissRequest = { planoCartaoId = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dados do cartão", style = MaterialTheme.typography.headlineSmall)
                Text("Os dados serão enviados ao Asaas apenas para processar a contratação.", style = MaterialTheme.typography.bodySmall)
                Campo("Nome no cartão", nome, { nome = it }, KeyboardType.Text)
                Campo("Número do cartão", numero, { numero = it.filter(Char::isDigit).take(19) }, KeyboardType.Number)
                Campo("Vencimento (MM/AA)", validade, { validade = it.filter { c -> c.isDigit() || c == '/' }.take(5) }, KeyboardType.Number)
                Campo("CVV", cvv, { cvv = it.filter(Char::isDigit).take(4) }, KeyboardType.Number)
                Campo("CPF do titular", cpf, { cpf = it.filter(Char::isDigit).take(11) }, KeyboardType.Number)
                Campo("CEP de cobrança", cep, { cep = it.filter(Char::isDigit).take(8) }, KeyboardType.Number)
                Campo("Número do endereço", numeroEndereco, { numeroEndereco = it }, KeyboardType.Text)
                Campo("Telefone com DDD", telefone, { telefone = it.filter(Char::isDigit).take(11) }, KeyboardType.Phone)
                erroCartao?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    val digits = numero.filter(Char::isDigit)
                    val parts = validade.split('/')
                    val expira = if (parts.size == 2 && parts[0].length == 2 && parts[1].length == 2) {
                        runCatching { YearMonth.of(2000 + parts[1].toInt(), parts[0].toInt()) }.getOrNull()
                    } else null
                    val now = YearMonth.now()
                    erroCartao = when {
                        nome.isBlank() -> "Informe o nome impresso no cartão."
                        digits.length !in 13..19 -> "Confira o número do cartão."
                        expira == null || expira.monthValue !in 1..12 || expira.isBefore(now) -> "Informe um vencimento válido no formato MM/AA."
                        cvv.length !in 3..4 -> "Confira o CVV."
                        cpf.length != 11 -> "Informe um CPF válido."
                        cep.length != 8 -> "Informe um CEP válido."
                        numeroEndereco.isBlank() -> "Informe o número do endereço de cobrança."
                        telefone.length !in 10..11 -> "Informe um telefone com DDD."
                        else -> null
                    }
                    if (erroCartao == null) {
                        viewModel.contratarCartao(planoCartaoId!!, DadosCartaoContratacao(
                            nome.trim(), digits, parts[0], "20${parts[1]}", cvv, cpf, cep, numeroEndereco.trim(), telefone
                        ))
                        planoCartaoId = null
                        numero = ""; cvv = ""
                    }
                }, enabled = !state.carregando, modifier = Modifier.fillMaxWidth()) { Text("Confirmar contratação") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

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
            items(state.planos, key = { it.id }) { plano ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(plano.nome, style = MaterialTheme.typography.titleMedium)
                        plano.diasRecorrencia.takeIf { it > 0 }?.let { Text("$it dias") }
                        plano.valor.takeIf { it > 0.0 }?.let { Text("R$ %.2f".format(it)) }
                        Button(onClick = {
                            if (formaPagamento == "PIX") viewModel.contratarPix(plano.id)
                            else { erroCartao = null; planoCartaoId = plano.id }
                        }, enabled = !state.carregando && plano.ativo) { Text("Selecionar e contratar") }
                    }
                }
            }
            item { Text("Assinaturas", style = MaterialTheme.typography.headlineSmall) }
            items(state.assinaturas, key = { it["documentId"].toString() }) { assinatura ->
                val id = assinatura["documentId"]?.toString().orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Plano ${assinatura["planoId"] ?: ""}", style = MaterialTheme.typography.titleMedium)
                        StatusChip(assinatura["status"]?.toString() ?: "PENDENTE")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.atualizarCobrancas(id) }) { Text("Atualizar cobranças") }
                            OutlinedButton(onClick = { viewModel.cancelar(id) }, enabled = assinatura["status"] != "CANCELADA") { Text("Cancelar") }
                        }
                    }
                }
            }
            item { Text("Cobranças", style = MaterialTheme.typography.headlineSmall) }
            items(state.cobrancas, key = { it["asaasPaymentId"]?.toString() ?: it.hashCode().toString() }) { cobranca ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(cobranca["status"]?.toString() ?: "PENDENTE")
                            Text(cobranca["formaPagamento"]?.toString().orEmpty())
                        }
                        Text("Vencimento: ${cobranca["vencimento"] ?: "—"}")
                        if (cobranca["formaPagamento"] == "PIX") OutlinedButton(onClick = { viewModel.obterPix(cobranca["documentId"].toString()) }) { Text("Exibir Pix copia e cola") }
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

@Composable
private fun Campo(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun PixDialog(encodedImage: String?, payload: String, onDismiss: () -> Unit, onCopy: () -> Unit) {
    val qr = remember(encodedImage) {
        encodedImage?.let { raw -> runCatching {
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pagamento via Pix") },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            qr?.let { Image(it, contentDescription = "QR Code Pix", modifier = Modifier.size(220.dp)) }
            Text("Pix copia e cola", style = MaterialTheme.typography.titleSmall)
            Text(payload, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = onCopy) { Text("Copiar código Pix") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
}
