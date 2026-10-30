package app.criba.pc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.criba.pc.Item
import app.criba.pc.Kind
import app.criba.pc.isScreenshot
import app.criba.pc.isWhatsApp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criba.pc.AppState
import app.criba.pc.Mode
import app.criba.pc.Section
import app.criba.pc.fmtBytes

@Composable
fun AppRoot(app: AppState) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { app.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(Unit) { app.start() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (app.root == null) Welcome(app) else Workspace(app)
            app.viewer?.let { Viewer(app, it) }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(20.dp))
        }
    }
    Dialogs(app)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Welcome(app: AppState) {
    val folders = remember { app.suggestedFolders() }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 620.dp).padding(40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Criba", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text(
                "Ordena las fotos y documentos de tu PC. Agrupa por momentos, encuentra duplicados, fotos borrosas, versiones viejas y lo que sobra. Todo se analiza aquí mismo, sin subir nada a internet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Text("Elige una carpeta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            folders.forEach { (label, f) ->
                OutlinedCard(onClick = { app.open(f) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            Text(f.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Button(onClick = { app.chooseRoot() }) { Text("Elegir otra carpeta…") }
            if (folders.any { it.first.contains("OneDrive") }) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "Si eliges una carpeta de OneDrive, lo que muevas o elimines aquí se sincroniza solo con la nube. Los archivos que están solo en la nube no se descargan: se muestran con una nube y se omiten del análisis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Workspace(app: AppState) {
    Row(Modifier.fillMaxSize()) {
        Sidebar(app)
        VerticalDivider()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(app)
            if (app.busy != null || app.scanning) LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) { Content(app) }
        }
    }
}

private fun sectionsFor(mode: Mode) =
    if (mode == Mode.PHOTOS) listOf(Section.LIBRARY, Section.EXPLORE, Section.CLEAN, Section.ORGANIZE)
    else listOf(Section.TYPES, Section.TOPICS, Section.EXPLORE, Section.CLEAN, Section.ORGANIZE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sidebar(app: AppState) {
    val r = app.report
    val root = app.root ?: return
    Column(Modifier.width(260.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Criba", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(root.name.ifEmpty { root.path }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f))
                }
                Text(root.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row {
                    TextButton(onClick = { app.chooseRoot() }) { Text("Cambiar") }
                    TextButton(onClick = { app.closeFolder() }) { Text("Inicio") }
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = app.mode == Mode.PHOTOS, onClick = { app.switchMode(Mode.PHOTOS) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Fotos") }
            SegmentedButton(selected = app.mode == Mode.DOCS, onClick = { app.switchMode(Mode.DOCS) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Documentos") }
        }
        Spacer(Modifier.size(4.dp))
        val suggestedCount = remember(r, app.mode) {
            val pool = if (app.mode == Mode.PHOTOS) r.media else r.docs
            pool.count { it.path in r.reasons }
        }
        sectionsFor(app.mode).forEach { s ->
            NavigationDrawerItem(
                label = { Text(s.label) },
                selected = app.section == s,
                onClick = { app.go(s) },
                icon = {
                    Icon(
                        when (s) {
                            Section.LIBRARY -> Icons.Filled.PhotoLibrary
                            Section.EXPLORE -> Icons.Filled.Folder
                            Section.TYPES -> Icons.Filled.Category
                            Section.TOPICS -> Icons.Filled.Topic
                            Section.CLEAN -> Icons.Filled.AutoAwesome
                            Section.ORGANIZE -> Icons.AutoMirrored.Filled.Sort
                        },
                        contentDescription = null,
                    )
                },
                badge = { if (s == Section.CLEAN && suggestedCount > 0) Badge { Text("$suggestedCount") } },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        TextButton(onClick = { app.showProtected = true }) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                when (val n = app.protectedDirs.size) {
                    0 -> "Proteger carpetas"
                    1 -> "1 carpeta protegida"
                    else -> "$n carpetas protegidas"
                }
            )
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                "${r.media.size} fotos y videos · ${fmtBytes(r.mediaBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${r.docs.size} documentos · ${fmtBytes(r.docBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (r.cloudOnly > 0) {
                Text("${r.cloudOnly} solo en la nube (no se analizan)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (app.analyzing && app.analyzeTotal > 0) {
                Text("Analizando ${app.analyzedCount} de ${app.analyzeTotal}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                LinearProgressIndicator(progress = { app.analyzedCount.toFloat() / app.analyzeTotal }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TopBar(app: AppState) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (app.sel.isNotEmpty()) {
            val list = app.selectedItems()
            IconButton(onClick = { app.sel.clear() }) { Icon(Icons.Filled.Close, contentDescription = "Quitar selección") }
            Text(
                "${list.size} seleccionados · ${fmtBytes(list.sumOf { it.size })}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { app.selectAllVisible() }) { Text("Seleccionar todo") }
            if (list.size == 1) TextButton(onClick = { app.reveal(list[0]) }) { Text("Mostrar en carpeta") }
            OutlinedButton(onClick = { app.chooseAndMove(list) }) {
                Icon(Icons.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Mover a…")
            }
            Button(
                onClick = { app.confirmTrash = list },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Eliminar")
            }
            return@Row
        }
        val review = app.review
        val folder: String? = null
        if (review != null) {
            IconButton(onClick = { app.review = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        }
        val r = app.report
        val title = when {
            review != null -> review.title
            folder != null -> java.io.File(folder).name
            else -> app.section.label
        }
        val sub = when {
            app.scanning -> "Buscando archivos… ${app.scanCount}"
            app.busy != null -> app.busy
            review != null -> null
            folder != null -> "${r.media.count { it.parent == folder }} elementos · $folder"
            app.section == Section.LIBRARY -> "${r.media.size} fotos y videos en ${r.moments.size} momentos"
            app.section == Section.EXPLORE -> "Navega tus carpetas y protege las que no quieres que se toquen"
            app.section == Section.TYPES || app.section == Section.TOPICS -> "${r.docs.size} documentos · ${fmtBytes(r.docBytes)}"
            app.section == Section.CLEAN -> "Revisa lo que sobra antes de eliminar"
            else -> "Mueve tus archivos a carpetas ordenadas"
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val gridSection = (app.mode == Mode.PHOTOS && (app.section == Section.LIBRARY || review != null)) || app.section == Section.EXPLORE
        if (gridSection) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Slider(value = app.tileSize, onValueChange = { app.setTile(it) }, valueRange = 110f..320f, modifier = Modifier.width(150.dp))
            Icon(Icons.Filled.Image, contentDescription = "Tamaño de las miniaturas", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        val searchable = review == null && app.section in setOf(Section.LIBRARY, Section.EXPLORE, Section.TYPES, Section.TOPICS)
        if (searchable) {
            OutlinedTextField(
                value = app.query,
                onValueChange = { app.query = it },
                placeholder = { Text("Buscar por nombre o carpeta") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (app.query.isNotEmpty()) IconButton(onClick = { app.query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.width(300.dp),
            )
        }
        IconButton(onClick = { app.rescan() }, enabled = !app.scanning && !app.analyzing) {
            Icon(Icons.Filled.Refresh, contentDescription = "Volver a escanear")
        }
    }
}

@Composable
private fun Content(app: AppState) {
    val review = app.review
    when {
        app.scanning && app.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator()
                Text("Buscando archivos… ${app.scanCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        review != null -> ReviewView(app, review)
        app.section == Section.LIBRARY -> LibraryView(app)
        app.section == Section.EXPLORE -> ExplorerView(app)
        app.section == Section.TYPES -> DocsGrouped(app, byTopic = false)
        app.section == Section.TOPICS -> DocsGrouped(app, byTopic = true)
        app.section == Section.CLEAN -> CleanView(app)
        else -> OrganizeView(app)
    }
}

private val KIND_FILTERS = listOf("Fotos", "Videos", "Capturas", "WhatsApp")

private fun kindMatch(it: Item, f: String?): Boolean = when (f) {
    null -> true
    "Fotos" -> it.kind == Kind.PHOTO && !isScreenshot(it)
    "Videos" -> it.kind == Kind.VIDEO
    "Capturas" -> isScreenshot(it)
    "WhatsApp" -> isWhatsApp(it)
    else -> true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryView(app: AppState) {
    val r = app.report
    val q = app.query.trim().lowercase()
    val f = app.typeFilter
    val (rows, flat) = remember(r, q, f) { libraryRows(r.moments, q) { kindMatch(it, f) } }
    val counts = remember(r) { KIND_FILTERS.map { k -> k to r.media.count { kindMatch(it, k) } }.filter { it.second > 0 } }
    val years = remember(rows) {
        val m = LinkedHashMap<Int, Int>()
        rows.forEachIndexed { i, row -> if (row is HeaderRow && row.big && row.year != 0 && row.year !in m) m[row.year] = i }
        m.toList()
    }
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    SideEffect { app.visibleItems = flat }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChipBar(counts, f) { app.typeFilter = it } }
            if (years.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.widthIn(max = 420.dp)) {
                    years.forEach { (y, idx) ->
                        TextButton(onClick = { scope.launch { state.scrollToItem(idx) } }) { Text("$y") }
                    }
                }
            }
        }
        if (flat.isEmpty()) {
            Empty(
                when {
                    q.isNotEmpty() -> "Nada coincide con «$q»."
                    f != null -> "No hay elementos de este tipo."
                    else -> "No hay fotos ni videos en esta carpeta. Prueba con Documentos o elige otra carpeta."
                }
            )
        } else {
            Box(Modifier.weight(1f)) { MediaGrid(app, rows, flat, state = state) }
        }
    }
}

@Composable
private fun Dialogs(app: AppState) {
    app.confirmTrash?.let { all ->
        val list = all.filterNot { app.isProtected(it) }
        val skipped = all.size - list.size
        AlertDialog(
            onDismissRequest = { app.confirmTrash = null },
            title = {
                Text(
                    when {
                        list.isEmpty() -> "Están en carpetas protegidas"
                        list.size == 1 -> "¿Eliminar ${list[0].name}?"
                        else -> "¿Eliminar ${list.size} archivos?"
                    }
                )
            },
            text = {
                Text(
                    if (list.isEmpty()) "Para eliminarlos, primero quita la protección de su carpeta en Explorar."
                    else "Ocupan ${fmtBytes(list.sumOf { it.size })}. Van a la Papelera de reciclaje, de donde puedes restaurarlos." +
                        (if (app.isOneDrive) " OneDrive también los manda a su papelera en línea." else "") +
                        (if (skipped > 0) " $skipped están en carpetas protegidas y no se tocarán." else "")
                )
            },
            confirmButton = {
                Button(
                    enabled = list.isNotEmpty(),
                    onClick = {
                        app.confirmTrash = null
                        app.trash(list)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { app.confirmTrash = null }) { Text("Cancelar") } },
        )
    }
    if (app.showProtected) ProtectedDialog(app)
    app.pendingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { app.pendingPlan = null },
            title = { Text("¿Ordenar ${plan.size} archivos?") },
            text = { Text("Se moverán a carpetas dentro de «${app.root?.name}/Ordenado». No se borra nada y puedes deshacerlo con un clic.") },
            confirmButton = {
                Button(onClick = {
                    app.pendingPlan = null
                    app.organize(plan)
                }) { Text("Ordenar") }
            },
            dismissButton = { TextButton(onClick = { app.pendingPlan = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProtectedDialog(app: AppState) {
    val root = app.root?.path.orEmpty()
    val dirs = app.protectedDirs.sortedBy { it.lowercase() }
    AlertDialog(
        onDismissRequest = { app.showProtected = false },
        title = { Text("Carpetas protegidas") },
        text = {
            Column(Modifier.widthIn(min = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Lo que está dentro de estas carpetas nunca se sugiere para eliminar, no se manda a la papelera ni se mueve al ordenar. Las carpetas que parecen proyectos se protegen solas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dirs.isEmpty()) {
                    Text("Aún no hay carpetas protegidas.", modifier = Modifier.padding(vertical = 8.dp))
                }
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    dirs.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(java.io.File(d).name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    d.removePrefix(root).trimStart('\\', '/') + if (app.isAutoProtected(d)) " · proyecto detectado" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { app.navigate(d); app.showProtected = false }) { Text("Ver") }
                            TextButton(onClick = { app.setProtected(d, false) }) { Text("Quitar") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { app.showProtected = false }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = { app.chooseAndProtect() }) { Text("Proteger otra carpeta…") } },
    )
}
