package app.criba.galeria.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import app.criba.galeria.core.AuthProvider
import app.criba.galeria.core.Entry
import app.criba.galeria.core.Lib
import app.criba.galeria.core.NeedLoginException
import app.criba.galeria.core.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Lo que cambia entre Android y Windows. */
class Platform(
    val auth: AuthProvider,
    val dataDir: File,
    val cacheDir: File,
    val isDesktop: Boolean,
    /** Texto con lo que hay que registrar en Azure (se muestra si falta configurar). */
    val setupInfo: String,
    val backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
    val dynamicScheme: @Composable (dark: Boolean) -> ColorScheme? = { null },
)

enum class Phase { Loading, Setup, Login, Ready }

/** Estado de la app; vive mientras viva el proceso. */
class CribaApp(val platform: Platform) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val repo = Repo(platform.auth, platform.dataDir)

    val phase = MutableStateFlow(Phase.Loading)
    val lib = MutableStateFlow(Lib.EMPTY)
    val syncing = MutableStateFlow(false)
    val syncCount = MutableStateFlow(0)
    val busy = MutableStateFlow<String?>(null)
    val gridThumb = MutableStateFlow("medium")
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            if (!platform.auth.configured) {
                phase.value = Phase.Setup
                return@launch
            }
            lib.value = withContext(Dispatchers.IO) { repo.loadLocal() }
            gridThumb.value = repo.gridThumb
            val ok = runCatching { platform.auth.restore() }.getOrDefault(false)
            phase.value = if (ok) Phase.Ready else Phase.Login
            if (ok) sync()
        }
    }

    fun signIn() {
        scope.launch {
            val ok = try {
                platform.auth.signIn()
            } catch (e: Exception) {
                say("No se pudo iniciar sesión: ${e.message ?: e::class.simpleName}")
                false
            }
            if (ok) {
                phase.value = Phase.Ready
                sync()
            }
        }
    }

    fun sync() {
        if (syncing.value) return
        scope.launch {
            syncing.value = true
            syncCount.value = 0
            try {
                withContext(Dispatchers.IO) {
                    repo.sync { n, partial ->
                        syncCount.value = n
                        if (partial) lib.value = repo.lib()
                    }
                }
                lib.value = withContext(Dispatchers.Default) { repo.lib() }
                gridThumb.value = repo.gridThumb
            } catch (e: NeedLoginException) {
                phase.value = Phase.Login
            } catch (e: Exception) {
                say("No se pudo sincronizar: ${e.message ?: e::class.simpleName}")
            } finally {
                syncing.value = false
            }
        }
    }

    fun delete(ids: List<String>, done: () -> Unit = {}) = work("Eliminando ${ids.size}…") {
        val n = repo.delete(ids)
        val fail = ids.size - n
        say(
            "$n eliminados · están en la papelera de OneDrive" +
                if (fail > 0) " · $fail no se pudieron eliminar" else ""
        )
        done
    }

    fun move(ids: List<String>, folderId: String, done: () -> Unit = {}) = work("Moviendo ${ids.size}…") {
        val n = repo.move(ids, folderId)
        val fail = ids.size - n
        val name = repo.lib().folderName(folderId)
        say(
            "$n movidos a $name" +
                if (fail > 0) " · $fail no se movieron (puede que ya exista uno con el mismo nombre)" else ""
        )
        done
    }

    fun createFolder(parentId: String, name: String, then: (Entry) -> Unit) = work("Creando carpeta…") {
        val e = repo.createFolder(parentId, name)
        if (e == null) {
            say("No se pudo crear la carpeta")
            ({})
        } else {
            ({ then(e) })
        }
    }

    /** Ejecuta una operación en segundo plano y refresca la biblioteca. */
    private fun work(label: String, block: suspend () -> (() -> Unit)) {
        scope.launch {
            busy.value = label
            try {
                val after = withContext(Dispatchers.IO) { block() }
                lib.value = withContext(Dispatchers.Default) { repo.lib() }
                after()
            } catch (e: NeedLoginException) {
                phase.value = Phase.Login
            } catch (e: Exception) {
                say("Algo falló: ${e.message ?: e::class.simpleName}")
            } finally {
                busy.value = null
            }
        }
    }

    fun signOut(clearImages: () -> Unit) {
        scope.launch {
            runCatching { platform.auth.signOut() }
            withContext(Dispatchers.IO) { repo.reset() }
            clearImages()
            lib.value = Lib.EMPTY
            phase.value = Phase.Login
        }
    }

    fun say(msg: String) {
        messages.tryEmit(msg)
    }
}
