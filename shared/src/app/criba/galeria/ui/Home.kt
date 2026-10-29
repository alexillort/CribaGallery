package app.criba.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criba.galeria.core.Entry
import app.criba.galeria.core.Graph
import app.criba.galeria.core.Lib
import app.criba.galeria.core.analyze
import app.criba.galeria.core.fmtBytes
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory

enum class Tab(val label: String) { Fotos("Fotos"), Albumes("Álbumes"), Limpiar("Limpiar") }

class Review(val title: String, val note: String, val items: List<Entry>)

/** Estado de navegación y selección de la interfaz. */
class Ui(defaultColumns: Int) {
    var tab by mutableStateOf(Tab.Fotos)
    val folderStack = mutableStateListOf<String>()
    var review by mutableStateOf<Review?>(null)
    val sel = mutableStateMapOf<String, Boolean>()
    var selectMode by mutableStateOf(false)
    var viewer by mutableStateOf<ViewerState?>(null)
    var confirmDelete by mutableStateOf<List<String>?>(null)
    var moveIds by mutableStateOf<List<String>?>(null)
    var columns by mutableIntStateOf(defaultColumns)

    val selecting: Boolean get() = selectMode || sel.isNotEmpty()

    fun toggle(id: String) {
        if (sel[id] == true) sel.remove(id) else sel[id] = true
    }

    fun clearSel() {
        sel.clear()
        selectMode = false
    }

    fun zoomIn() {
        val i = COLUMN_STEPS.indexOf(columns)
        if (i > 0) columns = COLUMN_STEPS[i - 1]
    }

    fun zoomOut() {
        val i = COLUMN_STEPS.indexOf(columns)
        if (i in 0 until COLUMN_STEPS.lastIndex) columns = COLUMN_STEPS[i + 1]
    }
}

@Composable
fun CribaRoot(app: CribaApp) {
    val dark = isSystemInDarkTheme()
    val scheme = app.platform.dynamicScheme(dark) ?: if (dark) CribaDark else CribaLight
    androidx.compose.material3.MaterialTheme(colorScheme = scheme) {
        setSingletonImageLoaderFactory { ctx -> buildImageLoader(ctx, app) }
        LaunchedEffect(Unit) { app.start() }
        val phase by app.phase.collectAsState()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (phase) {
                Phase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Phase.Setup -> SetupScreen(app)
                Phase.Login -> LoginScreen(app)
                Phase.Ready -> Home(app)
            }
        }
    }
}

@Composable
private fun LoginScreen(app: CribaApp) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Criba", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tus fotos de OneDrive, rápidas y ordenadas.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = { app.signIn() }) { Text("Iniciar sesión con Microsoft") }
        Spacer(Modifier.height(16.dp))
        Text(
            "Entras en la página oficial de Microsoft. Criba no ve tu contraseña y habla directo con tu OneDrive.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun SetupScreen(app: CribaApp) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Falta conectar la app con Azure", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("Esta versión se compiló sin el ID de la app. Registra la app en Azure con estos datos, guarda el ID en GitHub y vuelve a compilar:")
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            Text(
                app.platform.setupInfo,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(app: CribaApp) {
    val ui = remember { Ui(if (app.platform.isDesktop) 8 else 4) }
    val lib by app.lib.collectAsState()
    val syncing by app.syncing.collectAsState()
    val count by app.syncCount.collectAsState()
    val busy by app.busy.collectAsState()
    val gridThumb by app.gridThumb.collectAsState()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalPlatformContext.current
    LaunchedEffect(Unit) { app.messages.collect { snack.showSnackbar(it) } }

    val folderId = ui.folderStack.lastOrNull()
    val review = ui.review
    val currentList: List<Entry> = when {
        ui.tab == Tab.Limpiar && review != null -> review.items
        ui.tab == Tab.Albumes && folderId != null -> lib.mediaByParent[folderId].orEmpty()
        ui.tab == Tab.Fotos -> lib.media
        else -> emptyList()
    }

    app.platform.backHandler(ui.viewer != null) { ui.viewer = null }
    app.platform.backHandler(ui.viewer == null && ui.selecting) { ui.clearSel() }
    app.platform.backHandler(ui.viewer == null && !ui.selecting && ui.tab == Tab.Albumes && folderId != null) {
        ui.folderStack.removeAt(ui.folderStack.lastIndex)
    }
    app.platform.backHandler(ui.viewer == null && !ui.selecting && ui.tab == Tab.Limpiar && review != null) { ui.review = null }

    Scaffold(
        topBar = {
            Column {
                if (ui.selecting) {
                    TopAppBar(
                        navigationIcon = { IconButton(onClick = { ui.clearSel() }) { Icon(Icons.Filled.Close, contentDescription = "Cancelar selección") } },
                        title = { Text(if (ui.sel.isEmpty()) "Selecciona fotos" else "${ui.sel.size} seleccionados") },
                        actions = {
                            IconButton(onClick = { currentList.forEach { ui.sel[it.id] = true } }) {
                                Icon(Icons.Filled.SelectAll, contentDescription = "Seleccionar todo")
                            }
                        },
                    )
                } else {
                    val goUpFolder: () -> Unit = { ui.folderStack.removeAt(ui.folderStack.lastIndex) }
                    val closeReview: () -> Unit = { ui.review = null }
                    val back: (() -> Unit)? = when {
                        ui.tab == Tab.Albumes && folderId != null -> goUpFolder
                        ui.tab == Tab.Limpiar && review != null -> closeReview
                        else -> null
                    }
                    val title = when {
                        ui.tab == Tab.Albumes && folderId != null -> lib.folderName(folderId)
                        ui.tab == Tab.Limpiar && review != null -> review.title
                        else -> ui.tab.label
                    }
                    val subtitle = when {
                        syncing -> if (count > 0) "Sincronizando… $count elementos leídos" else "Sincronizando…"
                        busy != null -> busy!!
                        ui.tab == Tab.Fotos -> "${lib.media.size} fotos y videos · ${fmtBytes(lib.totalBytes)}"
                        ui.tab == Tab.Albumes && folderId != null -> "${currentList.size} elementos"
                        ui.tab == Tab.Limpiar && review != null -> review.note
                        else -> null
                    }
                    var menu by remember { mutableStateOf(false) }
                    TopAppBar(
                        navigationIcon = {
                            if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                        },
                        title = {
                            Column {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (subtitle != null) {
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        },
                        actions = {
                            if (currentList.isNotEmpty()) {
                                IconButton(onClick = { ui.zoomOut() }) { Icon(Icons.Filled.ZoomOut, contentDescription = "Fotos más pequeñas") }
                                IconButton(onClick = { ui.zoomIn() }) { Icon(Icons.Filled.ZoomIn, contentDescription = "Fotos más grandes") }
                                IconButton(onClick = { ui.selectMode = true }) { Icon(Icons.Filled.Checklist, contentDescription = "Seleccionar") }
                            }
                            IconButton(onClick = { app.sync() }, enabled = !syncing) { Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar") }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Cerrar sesión") },
                                        onClick = {
                                            menu = false
                                            app.signOut { runCatching { SingletonImageLoader.get(ctx).let { it.diskCache?.clear(); it.memoryCache?.clear() } } }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
                if (syncing || busy != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            if (ui.selecting) {
                BottomAppBar {
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = ui.sel.isNotEmpty(), onClick = { ui.moveIds = ui.sel.keys.toList() }) {
                        Icon(Icons.Filled.DriveFileMove, contentDescription = null)
                        Text("  Mover")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = ui.sel.isNotEmpty(), onClick = { ui.confirmDelete = ui.sel.keys.toList() }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("  Eliminar")
                    }
                    Spacer(Modifier.weight(1f))
                }
            } else {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = ui.tab == t,
                            onClick = { ui.tab = t },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.Fotos -> Icons.Filled.PhotoLibrary
                                        Tab.Albumes -> Icons.Filled.Folder
                                        Tab.Limpiar -> Icons.Filled.CleaningServices
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (ui.tab) {
                Tab.Fotos -> PhotosTab(lib, ui, gridThumb, syncing)
                Tab.Albumes -> AlbumsTab(lib, ui, gridThumb)
                Tab.Limpiar -> CleanTab(lib, ui, gridThumb)
            }
        }
    }

    ui.viewer?.let { Viewer(lib, it, ui) }

    ui.confirmDelete?.let { ids ->
        DeleteDialog(
            count = ids.size,
            onConfirm = {
                ui.confirmDelete = null
                app.delete(ids) { ids.forEach { ui.sel.remove(it) } }
            },
            onDismiss = { ui.confirmDelete = null },
        )
    }

    ui.moveIds?.let { ids ->
        MoveDialog(
            lib = lib,
            count = ids.size,
            onMove = { folder ->
                ui.moveIds = null
                app.move(ids, folder) { ui.clearSel() }
            },
            onCreate = { parent, name, then -> app.createFolder(parent, name) { then(it.id) } },
            onDismiss = { ui.moveIds = null },
        )
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhotosTab(lib: Lib, ui: Ui, gridThumb: String, syncing: Boolean) {
    if (lib.media.isEmpty()) {
        Empty(if (syncing) "Leyendo tu OneDrive por primera vez… Las fotos aparecen mientras avanza." else "No hay fotos ni videos en tu OneDrive.")
        return
    }
    MediaGrid(lib.media, ui, gridThumb) { i -> ui.viewer = ViewerState(lib.media, i) }
}

@Composable
private fun AlbumsTab(lib: Lib, ui: Ui, gridThumb: String) {
    val folderId = ui.folderStack.lastOrNull()
    if (folderId == null) {
        val albums = remember(lib) {
            lib.folders.values
                .mapNotNull { f -> lib.mediaByParent[f.id]?.takeIf { it.isNotEmpty() }?.let { f to it } }
                .sortedByDescending { it.second.first().taken }
        }
        if (albums.isEmpty()) {
            Empty("Aquí aparecerán las carpetas de OneDrive que tengan fotos o videos.")
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.first.id }) { (f, media) ->
                Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { ui.folderStack.add(f.id) }) {
                    AsyncImage(
                        model = Graph.thumbUrl(media.first().id, gridThumb),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Text(
                        if (f.isRoot) "OneDrive (raíz)" else f.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                    )
                    Text("${media.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    } else {
        val media = lib.mediaByParent[folderId].orEmpty()
        val subs = lib.foldersByParent[folderId].orEmpty()
        val header: (@Composable () -> Unit)? = if (subs.isEmpty()) null else {
            {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subs.forEach { s ->
                        AssistChip(
                            onClick = { ui.folderStack.add(s.id) },
                            label = { Text(s.name) },
                            leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }
        if (media.isEmpty() && header == null) {
            Empty("Esta carpeta no tiene fotos ni videos.")
            return
        }
        MediaGrid(media, ui, gridThumb, header = header) { i -> ui.viewer = ViewerState(media, i) }
    }
}

@Composable
private fun CleanTab(lib: Lib, ui: Ui, gridThumb: String) {
    val review = ui.review
    if (review != null) {
        val live = review.items.filter { lib.byId.containsKey(it.id) }
        if (live.isEmpty()) {
            Empty("Listo, ya no queda nada en este grupo.")
            return
        }
        MediaGrid(live, ui, gridThumb, byDay = false) { i -> ui.viewer = ViewerState(live, i) }
        return
    }
    val c = remember(lib) { analyze(lib) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("Libera espacio en OneDrive", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Revisa cada grupo antes de eliminar. Lo eliminado va a la papelera de OneDrive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            CleanCard("Duplicados", "${c.dupExtras.size} copias exactas · ${fmtBytes(c.dupExtras.sumOf { it.size })}", c.dupExtras, gridThumb) {
                ui.review = Review("Duplicados", "Ya seleccioné las copias; se conserva una de cada grupo", c.dupGroups.flatten())
                ui.sel.clear()
                c.dupExtras.forEach { ui.sel[it.id] = true }
            }
        }
        item {
            CleanCard("Capturas de pantalla", "${c.screenshots.size} · ${fmtBytes(c.screenshots.sumOf { it.size })}", c.screenshots, gridThumb) {
                ui.review = Review("Capturas de pantalla", "Selecciona las que ya no necesitas", c.screenshots)
            }
        }
        item {
            CleanCard("Videos pesados", "${c.bigVideos.size} de más de 150 MB · ${fmtBytes(c.bigVideos.sumOf { it.size })}", c.bigVideos, gridThumb) {
                ui.review = Review("Videos pesados", "Ordenados del más pesado al más ligero", c.bigVideos)
            }
        }
        item {
            CleanCard("WhatsApp", "${c.whatsapp.size} · ${fmtBytes(c.whatsapp.sumOf { it.size })}", c.whatsapp, gridThumb) {
                ui.review = Review("WhatsApp", "Fotos y videos recibidos por WhatsApp", c.whatsapp)
            }
        }
    }
}

@Composable
private fun CleanCard(title: String, subtitle: String, items: List<Entry>, gridThumb: String, onOpen: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = items.isNotEmpty(), onClick = onOpen)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (items.isNotEmpty()) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.take(5).forEach { e ->
                        AsyncImage(
                            model = Graph.thumbUrl(e.id, gridThumb),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            } else {
                Text("Nada por aquí.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
