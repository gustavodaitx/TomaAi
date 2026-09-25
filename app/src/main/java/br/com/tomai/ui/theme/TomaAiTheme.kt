package br.com.tomai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TomaAiLightColorScheme = lightColorScheme(
    primary = BlueHealthPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EAF8),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = BlueHealthDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4EAF8),
    onSecondaryContainer = Color(0xFF001D32),
    tertiary = GreenSuccess,
    onTertiary = Color.White,
    tertiaryContainer = SuccessCardLight,
    onTertiaryContainer = Color(0xFF00210A),
    error = Color(0xFFBA1A1A),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = Color.White,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFE7EFF4),
    onSurfaceVariant = Color(0xFF40484F),
    outline = Color(0xFF707980)
)

private val TomaAiDarkColorScheme = darkColorScheme(
    primary = Color(0xFF83CFFF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B70),
    onPrimaryContainer = Color(0xFFC5E7FF),
    secondary = Color(0xFF9CCBEB),
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF174B69),
    onSecondaryContainer = Color(0xFFD0E8FA),
    tertiary = Color(0xFF9DD49A),
    onTertiary = Color(0xFF00390B),
    tertiaryContainer = Color(0xFF17501D),
    onTertiaryContainer = Color(0xFFB9F0B4),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF101417),
    onBackground = Color(0xFFE0E3E6),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFE0E3E6),
    surfaceVariant = Color(0xFF40484F),
    onSurfaceVariant = Color(0xFFC0C8CF),
    outline = Color(0xFF899198)
)

@Composable
fun TomaAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TomaAiDarkColorScheme else TomaAiLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
