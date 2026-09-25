package br.com.tomai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// PALETA DE CORES DA LOGÓTIPO TOMA AÍ
// ==========================================
val LogoNavy = Color(0xFF0C2340)       // Azul escuro dos contornos e texto "Toma"
val LogoPurple = Color(0xFF8A54F7)     // Roxo do "Aí" e detalhes
val LogoBlueLight = Color(0xFF1E88E5)  // Azul da pílula
val LogoPink = Color(0xFFFF8A8A)       // Bochechas rosadas
val LogoWhite = Color(0xFFFFFFFF)

@Composable
fun TomaAiLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // ----------------------------------------------------
        // 1. DESENHO DO MASCOTE (Pílula + Relógio)
        // ----------------------------------------------------
        Box(
            modifier = Modifier
                .size(size)
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.toPx()
                val height = size.toPx()

                // Rotação leve para a pílula ficar inclinada (como na imagem)
                rotate(degrees = -20f, pivot = Offset(width * 0.45f, height * 0.45f)) {
                    val strokeWidth = width * 0.06f
                    val pillWidth = width * 0.38f
                    val pillHeight = height * 0.72f
                    val pillLeft = width * 0.22f
                    val pillTop = height * 0.1f

                    // Metade superior da pílula (Branca)
                    drawRoundRect(
                        color = LogoWhite,
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillWidth / 2, pillWidth / 2)
                    )

                    // Metade inferior da pílula (Azul)
                    val pathBlue = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = pillLeft,
                                top = pillTop,
                                right = pillLeft + pillWidth,
                                bottom = pillTop + pillHeight,
                                cornerRadius = CornerRadius(pillWidth / 2, pillWidth / 2)
                            )
                        )
                    }
                    clipPath(pathBlue) {
                        drawRect(
                            color = LogoBlueLight,
                            topLeft = Offset(pillLeft, pillTop + (pillHeight / 2)),
                            size = Size(pillWidth, pillHeight / 2)
                        )
                        // Divisória no meio
                        drawLine(
                            color = LogoNavy,
                            start = Offset(pillLeft, pillTop + (pillHeight / 2)),
                            end = Offset(pillLeft + pillWidth, pillTop + (pillHeight / 2)),
                            strokeWidth = strokeWidth * 0.7f
                        )
                    }

                    // Contorno da pílula
                    drawRoundRect(
                        color = LogoNavy,
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillWidth / 2, pillWidth / 2),
                        style = Stroke(width = strokeWidth)
                    )

                    // Olho piscando (Esquerda)
                    val eyePath = Path().apply {
                        moveTo(pillLeft + pillWidth * 0.25f, pillTop + pillHeight * 0.3f)
                        cubicTo(
                            pillLeft + pillWidth * 0.35f, pillTop + pillHeight * 0.22f,
                            pillLeft + pillWidth * 0.35f, pillTop + pillHeight * 0.22f,
                            pillLeft + pillWidth * 0.45f, pillTop + pillHeight * 0.3f
                        )
                    }
                    drawPath(
                        path = eyePath,
                        color = LogoNavy,
                        style = Stroke(width = strokeWidth * 0.7f, cap = StrokeCap.Round)
                    )

                    // Olho aberto (Direita)
                    drawCircle(
                        color = LogoNavy,
                        radius = pillWidth * 0.09f,
                        center = Offset(pillLeft + pillWidth * 0.72f, pillTop + pillHeight * 0.28f)
                    )

                    // Bochechas rosadas
                    drawCircle(
                        color = LogoPink,
                        radius = pillWidth * 0.12f,
                        center = Offset(pillLeft + pillWidth * 0.2f, pillTop + pillHeight * 0.38f)
                    )
                    drawCircle(
                        color = LogoPink,
                        radius = pillWidth * 0.12f,
                        center = Offset(pillLeft + pillWidth * 0.82f, pillTop + pillHeight * 0.38f)
                    )

                    // Sorriso
                    val mouthPath = Path().apply {
                        moveTo(pillLeft + pillWidth * 0.42f, pillTop + pillHeight * 0.38f)
                        cubicTo(
                            pillLeft + pillWidth * 0.55f, pillTop + pillHeight * 0.48f,
                            pillLeft + pillWidth * 0.55f, pillTop + pillHeight * 0.48f,
                            pillLeft + pillWidth * 0.68f, pillTop + pillHeight * 0.38f
                        )
                    }
                    drawPath(
                        path = mouthPath,
                        color = LogoNavy,
                        style = Stroke(width = strokeWidth * 0.6f, cap = StrokeCap.Round)
                    )

                    // Raios de brilho azul no topo esquerdo
                    val sparkColor = LogoBlueLight
                    drawLine(
                        sparkColor,
                        Offset(pillLeft - 10f, pillTop + 10f),
                        Offset(pillLeft - 25f, pillTop - 5f),
                        strokeWidth = strokeWidth * 0.7f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        sparkColor,
                        Offset(pillLeft + 10f, pillTop - 10f),
                        Offset(pillLeft + 5f, pillTop - 30f),
                        strokeWidth = strokeWidth * 0.7f,
                        cap = StrokeCap.Round
                    )
                }

                // ----------------------------------------------------
                // RELÓGIO COM CHECKMARK (Canto inferior direito)
                // ----------------------------------------------------
                val clockRadius = width * 0.22f
                val clockCenter = Offset(width * 0.75f, height * 0.72f)

                // Fundo do relógio
                drawCircle(color = LogoWhite, radius = clockRadius, center = clockCenter)

                // Contorno do relógio
                drawCircle(
                    color = LogoNavy,
                    radius = clockRadius,
                    center = clockCenter,
                    style = Stroke(width = width * 0.05f)
                )

                // Visto (Checkmark) dentro do relógio
                val checkPath = Path().apply {
                    moveTo(clockCenter.x - clockRadius * 0.4f, clockCenter.y)
                    lineTo(clockCenter.x - clockRadius * 0.1f, clockCenter.y + clockRadius * 0.35f)
                    lineTo(clockCenter.x + clockRadius * 0.45f, clockCenter.y - clockRadius * 0.3f)
                }
                drawPath(
                    path = checkPath,
                    color = LogoNavy,
                    style = Stroke(width = width * 0.055f, cap = StrokeCap.Round)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ----------------------------------------------------
        // 2. TEXTO DA MARCA "TomaAí" COM SUBLINHADO
        // ----------------------------------------------------
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = LogoNavy)) {
                            append("Toma")
                        }
                        withStyle(style = SpanStyle(color = LogoPurple)) {
                            append("Aí")
                        }
                    },
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )

                // Raios roxos sobre a letra "í"
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(bottom = 8.dp)
                ) {
                    val w = sizeToPx().width
                    val h = sizeToPx().height

                    // Raios de destaque do "í"
                    val sparkPurple = LogoPurple
                    val strokeW = 6f
                    drawLine(
                        sparkPurple,
                        Offset(w * 0.92f, h * 0.1f),
                        Offset(w * 0.98f, h * 0.01f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        sparkPurple,
                        Offset(w * 0.85f, h * 0.05f),
                        Offset(w * 0.88f, -h * 0.08f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Sublinhado Curvo (Roxo)
            Canvas(
                modifier = Modifier
                    .width(size * 1.1f)
                    .height(12.dp)
            ) {
                val underlinePath = Path().apply {
                    moveTo(sizeToPx().width * 0.2f, 2f)
                    cubicTo(
                        sizeToPx().width * 0.6f, sizeToPx().height * 1.2f,
                        sizeToPx().width * 0.6f, sizeToPx().height * 1.2f,
                        sizeToPx().width * 0.98f, 2f
                    )
                }
                drawPath(
                    path = underlinePath,
                    color = LogoPurple,
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

// Helper para converter tamanhos no Canvas
private fun androidx.compose.ui.graphics.drawscope.DrawScope.sizeToPx() = this.size

// ==========================================
// PREVIEW PARA VISUALIZAR NO ANDROID STUDIO
// ==========================================
@Preview(showBackground = true)
@Composable
fun TomaAiLogoPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        TomaAiLogo(size = 100.dp)
    }
}
