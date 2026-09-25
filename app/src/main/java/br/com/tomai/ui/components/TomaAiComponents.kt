package br.com.tomai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.tomai.ui.theme.BlueHealthDark
import br.com.tomai.ui.theme.PendingContainerDark
import br.com.tomai.ui.theme.PendingContainerLight
import br.com.tomai.ui.theme.PendingDark
import br.com.tomai.ui.theme.PendingLight
import br.com.tomai.ui.theme.SuccessCardLight
import br.com.tomai.ui.theme.TomaAiTheme

@Composable
fun TomaAiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) = AppButton(text, onClick, modifier, isLoading, enabled)

@Composable
fun TomaAiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) = AppTextField(value, onValueChange, label, modifier, leadingIcon = leadingIcon,
    trailingIcon = trailingIcon, isError = isError, errorMessage = errorMessage)

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val normalized = status.uppercase()
    val dark = isSystemInDarkTheme()
    val (container, content) = when {
        normalized.contains("ATIV") || normalized.contains("RECEB") || normalized.contains("CONFIRM") || normalized.contains("TOMAD") ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        normalized.contains("PEND") || normalized.contains("ATRAS") ->
            (if (dark) PendingContainerDark else PendingContainerLight) to (if (dark) PendingDark else PendingLight)
        normalized.contains("CANCEL") || normalized.contains("VENC") || normalized.contains("INAD") || normalized.contains("NAO") || normalized.contains("NÃO") ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = modifier, color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(status.replace('_', ' '), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DoseCard(
    nome: String,
    dosagem: String,
    horario: String,
    status: String,
    modifier: Modifier = Modifier,
    onConfirmarClick: (() -> Unit)? = null,
    podeConfirmar: Boolean = true,
    actions: (@Composable () -> Unit)? = null
) {
    val normalized = status.uppercase()
    val confirmado = normalized.contains("CONFIRM") || normalized.contains("TOMAD")
    val container = when {
        confirmado && !isSystemInDarkTheme() -> SuccessCardLight
        confirmado -> MaterialTheme.colorScheme.tertiaryContainer
        normalized.contains("NAO") || normalized.contains("NÃO") || normalized.contains("IGNOR") ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f)
        else -> MaterialTheme.colorScheme.surface
    }
    val animatedContainer by animateColorAsState(container, label = "doseStatusBackground")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = animatedContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(horario, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = BlueHealthDark)
                    Text(nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Dosagem: $dosagem", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onConfirmarClick != null && actions == null) {
                    IconButton(
                        onClick = onConfirmarClick,
                        enabled = podeConfirmar && !confirmado,
                        modifier = Modifier.size(52.dp).background(
                            color = if (confirmado) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        )
                    ) {
                        Icon(
                            imageVector = if (confirmado) Icons.Default.Check else Icons.Default.Notifications,
                            contentDescription = if (confirmado) "Dose confirmada" else "Confirmar dose de $nome",
                            tint = if (confirmado) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            StatusChip(status)
            actions?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarTomaAi(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold)
                    if (title != "TomaAí") Text("TomaAí", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Preview(showBackground = true)
@Composable
private fun TomaAiComponentsPreview() {
    TomaAiTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TopAppBarTomaAi(title = "Lembretes")
            TomaAiTextField(value = "", onValueChange = {}, label = "Buscar medicamento")
            StatusChip("PENDENTE")
            DoseCard("Vitamina D", "1 comprimido", "08:00", "PENDENTE", onConfirmarClick = {})
            TomaAiButton(text = "Continuar", onClick = {})
        }
    }
}
