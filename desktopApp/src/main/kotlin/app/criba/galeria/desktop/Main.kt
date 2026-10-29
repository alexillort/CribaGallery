package app.criba.galeria.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.criba.galeria.ui.CribaApp
import app.criba.galeria.ui.CribaRoot
import app.criba.galeria.ui.Platform
import java.io.File

private fun appDir(): File {
    val local = System.getenv("LOCALAPPDATA")
    val base = if (!local.isNullOrBlank()) File(local, "Criba") else File(System.getProperty("user.home"), ".criba")
    return base.also { it.mkdirs() }
}

fun main() = application {
    val app = remember {
        val dir = appDir()
        CribaApp(
            Platform(
                auth = DesktopAuth(Config.CLIENT_ID, dir),
                dataDir = File(dir, "datos"),
                cacheDir = File(dir, "cache"),
                isDesktop = true,
                setupInfo = """
                    Plataforma: Aplicaciones móviles y de escritorio
                    Redirect URI: http://localhost
                    Permiso de Microsoft Graph: Files.ReadWrite (delegado)
                """.trimIndent(),
                backHandler = { _, _ -> },
            )
        )
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Criba",
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
    ) {
        CribaRoot(app)
    }
}
