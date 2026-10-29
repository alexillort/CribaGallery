package app.criba.galeria.droid

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import app.criba.galeria.BuildConfig
import app.criba.galeria.ui.CribaApp
import app.criba.galeria.ui.CribaRoot
import app.criba.galeria.ui.Platform

/** La app vive mientras viva el proceso; así no se pierde nada al girar la pantalla. */
object AppHolder {
    private var app: CribaApp? = null

    fun get(ctx: Context): CribaApp = app ?: CribaApp(
        Platform(
            auth = AndroidAuth(ctx.applicationContext),
            dataDir = ctx.filesDir,
            cacheDir = ctx.cacheDir,
            isDesktop = false,
            setupInfo = """
                Plataforma: Android
                Nombre del paquete: app.criba.galeria
                Hash de la firma: ${BuildConfig.MSAL_HASH}
                Redirect URI: ${BuildConfig.MSAL_REDIRECT}
                Permiso de Microsoft Graph: Files.ReadWrite (delegado)
            """.trimIndent(),
            backHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
            dynamicScheme = { dark ->
                if (Build.VERSION.SDK_INT >= 31) {
                    val c = LocalContext.current
                    if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
                } else {
                    null
                }
            },
        )
    ).also { app = it }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = AppHolder.get(this)
        (app.platform.auth as AndroidAuth).activity = this
        setContent { CribaRoot(app) }
    }

    override fun onResume() {
        super.onResume()
        (AppHolder.get(this).platform.auth as AndroidAuth).activity = this
    }
}
