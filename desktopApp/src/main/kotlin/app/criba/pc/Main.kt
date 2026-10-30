package app.criba.pc

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.criba.pc.ui.AppRoot
import java.io.File
import javax.swing.UIManager

val CribaLight = lightColorScheme(
    primary = Color(0xFF1F4FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5F8),
    onPrimaryContainer = Color(0xFF0B2459),
    secondary = Color(0xFF52607A),
    secondaryContainer = Color(0xFFE3E8F2),
    tertiary = Color(0xFFB7791F),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFF6F7F9),
    surfaceVariant = Color(0xFFE2E6EC),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF1F5),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

val CribaDark = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF0B2459),
    primaryContainer = Color(0xFF233F7A),
    onPrimaryContainer = Color(0xFFDCE5F8),
    secondary = Color(0xFFB9C6E0),
    secondaryContainer = Color(0xFF2C3446),
    tertiary = Color(0xFFF0C27A),
    background = Color(0xFF0F1114),
    surface = Color(0xFF0F1114),
    surfaceVariant = Color(0xFF262A30),
    surfaceContainerLow = Color(0xFF16191D),
    surfaceContainer = Color(0xFF1B1F24),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1A17),
)

@Composable
fun CribaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) CribaDark else CribaLight, content = content)
}

private fun appDataDir(): File {
    val local = System.getenv("LOCALAPPDATA")
    val base = if (!local.isNullOrBlank()) File(local, "Criba") else File(System.getProperty("user.home"), ".criba")
    return base.also { it.mkdirs() }
}

fun main() {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    application {
        val app = remember { AppState(appDataDir()) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Criba",
            state = rememberWindowState(width = 1380.dp, height = 900.dp),
            onPreviewKeyEvent = { app.onKey(it) },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(1000, 660) }
            CribaTheme { AppRoot(app) }
        }
    }
}
