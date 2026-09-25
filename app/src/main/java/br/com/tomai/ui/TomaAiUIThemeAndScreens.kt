package br.com.tomai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.tomai.ui.components.DoseCard
import br.com.tomai.ui.theme.TomaAiTheme

@Preview(showBackground = true)
@Composable
private fun TomaAiUIThemeAndScreensPreview() {
    TomaAiTheme {
        Column(Modifier.padding(16.dp)) {
            DoseCard(
                nome = "Vitamina D",
                dosagem = "1 comprimido",
                horario = "08:00",
                status = "PENDENTE",
                onConfirmarClick = {}
            )
        }
    }
}
