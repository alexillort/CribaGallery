package app.criba.pc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criba.pc.AppState
import app.criba.pc.Item
import app.criba.pc.Kind
import app.criba.pc.SortBy
import app.criba.pc.docType
import app.criba.pc.fmtBytes
import app.criba.pc.shortDate
import java.io.File

class FolderInfo(val path: String, val name: String) {
    var count = 0
    var bytes = 0L
    var cover: Item? = null
}

class DirView(val folders: List<FolderInfo>, val files: List<Item>)

/** Qué hay dentro de una carpeta: sus subcarpetas (con tamaño) y sus archivos. */
fun dirView(items: List<Item>, cur: String, deep: Boolean): DirView {
    val prefix = cur + File.separator
    val folders = LinkedHashMap<String, FolderInfo>()
    val files = ArrayList<Item>()
    for (it in items) {
        if (!it.path.startsWith(prefix)) continue
        val rel = it.path.substring(prefix.length)
        val cut = rel.indexOf(File.separatorChar)
        if (cut < 0 || deep) files += it
        if (cut >= 0) {
            val name = rel.substring(0, cut)
            val f = folders.getOrPut(name) { FolderInfo(prefix + name, name) }
            f.count++
            f.bytes += it.size
            if (f.cover == null && it.kind == Kind.PHOTO && !it.cloudOnly) f.cover = it
        }
    }
    if (!deep) {
        // Carpetas vacías o sin archivos reconocidos también se muestran.
        File(cur).listFiles()?.forEach { f ->
            if (f.isDirectory && !f.isHidden && !f.name.startsWith(".") && !f.name.startsWith("$") && f.name !in folders) {
                folders[f.name] = FolderInfo(f.path, f.name)
            }
        }
    }
    return DirView(if (deep) emptyList() else folders.values.sortedBy { it.name.lowercase() }, files)
}

fun typeLabel(it: Item): String = when (it.kind) {
    Kind.PHOTO -> "Fotos"
    Kind.VIDEO -> "Videos"
    Kind.DOC -> docType(it.ext).label
}

const val FOLDERS_LABEL = "Carpetas"

sealed interface ExRow {
    val key: String
}

class ExHeader(val title: String, override val key: String) : ExRow
class ExFolder(val f: FolderInfo) : ExRow {
    override val key: String get() = "f:" + f.path
}
class ExFile(val item: Item, val mediaIndex: Int) : ExRow {
    override val key: String get() = item.path
}

@Composable
fun ExplorerView(app: AppState) {
    val root = app.root ?: return
    val cur = app.exploreDir ?: root.path
    val view = remember(app.items, cur, app.exploreDeep, app.fsVersion) { dirView(app.items, cur, app.exploreDeep) }
    val q = app.query.trim().lowercase()
    val filter = app.typeFilter
    val chips = remember(view) {
        val out = ArrayList<Pair<String, Int>>()
        if (view.folders.isNotEmpty()) out += FOLDERS_LABEL to view.folders.size
        out += view.files.groupBy { typeLabel(it) }.map { it.key to it.value.size }.sortedByDescending { it.second }
        out
    }
    val (rows, media, files) = remember(view, filter, app.sortBy, app.stackByType, q) {
        val folders = if (filter == null || filter == FOLDERS_LABEL) view.folders.filter { q.isEmpty() || it.name.lowercase().contains(q) } else emptyList()
        val sorter: Comparator<Item> = when (app.sortBy) {
            SortBy.NAME -> compareBy<Item> { it.name.lowercase() }
            SortBy.DATE -> compareByDescending<Item> { if (it.isMedia) it.taken else it.modified }
            SortBy.SIZE -> compareByDescending<Item> { it.size }
        }
        val files = if (filter == FOLDERS_LABEL) emptyList() else view.files
            .filter { (filter == null || typeLabel(it) == filter) && (q.isEmpty() || matches(it, q)) }
            .sortedWith(sorter)
        val rows = ArrayList<ExRow>()
        val media = ArrayList<Item>()
        if (folders.isNotEmpty()) {
            rows += ExHeader("Carpetas · ${folders.size}", "h:folders")
            folders.forEach { rows += ExFolder(it) }
        }
        val groups: List<Pair<String, List<Item>>> =
            if (app.stackByType) files.groupBy { typeLabel(it) }.toList().sortedByDescending { it.second.size }
            else listOf("Archivos" to files)
        for ((title, list) in groups) {
            if (list.isEmpty()) continue
            rows += ExHeader("$title · ${list.size} · ${fmtBytes(list.sumOf { it.size })}", "h:$title")
            for (it in list) {
                rows += ExFile(it, if (it.isMedia) media.size else -1)
                if (it.isMedia) media += it
            }
        }
        Triple(rows, media, files)
    }
    SideEffect { app.visibleItems = files }

    Column(Modifier.fillMaxSize()) {
        ExplorerBar(app, root, cur, chips)
        if (rows.isEmpty()) {
            Empty(if (q.isNotEmpty()) "Nada coincide con «$q» en esta carpeta." else "Esta carpeta está vacía.")
            return@Column
        }
        val state = rememberLazyGridState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
          androidx.compose.foundation.ContextMenuArea(items = { backgroundMenu(app, cur) }) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(app.tileSize.dp),
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 24.dp, bottom = 32.dp),
            ) {
                items(
                    rows,
                    key = { it.key },
                    span = { if (it is ExHeader) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                    contentType = { when (it) { is ExHeader -> 0; is ExFolder -> 1; is ExFile -> if (it.item.isMedia) 2 else 3 } },
                ) { row ->
                    when (row) {
                        is ExHeader -> Text(
                            row.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 2.dp),
                        )
                        is ExFolder -> androidx.compose.foundation.ContextMenuArea(items = { folderMenu(app, row.f.path) }) { FolderTile(app, row.f) }
                        is ExFile -> if (row.item.isMedia) MediaTile(app, row.item, media, row.mediaIndex, showReasons = false, typeBadge = true)
                        else androidx.compose.foundation.ContextMenuArea(items = { fileMenu(app, row.item) { app.openFile(row.item) } }) { FileTile(app, row.item) }
                    }
                }
            }
          }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExplorerBar(app: AppState, root: File, cur: String, chips: List<Pair<String, Int>>) {
    val isRoot = cur == root.path
    val prot = app.isProtectedDir(cur)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                val parts = cur.removePrefix(root.path).split(File.separatorChar).filter { it.isNotEmpty() }
                Crumb(root.name.ifEmpty { root.path }, parts.isEmpty()) { app.navigate(null) }
                var acc = root.path
                parts.forEachIndexed { i, p ->
                    acc = acc + File.separator + p
                    val target = acc
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Crumb(p, i == parts.lastIndex) { app.navigate(target) }
                }
            }
            if (!isRoot) {
                OutlinedButton(onClick = { app.setProtected(cur, !prot) }) {
                    Icon(if (prot) Icons.Filled.Lock else Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (prot) "Protegida" else "Proteger")
                }
            }
            app.clip?.let { c ->
                Button(onClick = { app.paste(cur) }) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Pegar aquí (${c.targets.count})")
                }
                TextButton(onClick = { app.clip = null }) { Text("Cancelar") }
            }
            TextButton(onClick = { app.creatingFolder = true }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Nueva carpeta")
            }
            TextButton(onClick = { app.openFolderInExplorer(cur) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Abrir en el Explorador")
            }
        }
        if (prot) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                Text(
                    if (app.isAutoProtected(cur)) "Carpeta protegida porque parece un proyecto. Nada de adentro se sugiere para eliminar, se elimina ni se ordena."
                    else "Carpeta protegida. Nada de adentro se sugiere para eliminar, se elimina ni se ordena.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = app.typeFilter == null, onClick = { app.typeFilter = null }, label = { Text("Todo") })
            chips.forEach { (label, n) ->
                FilterChip(
                    selected = app.typeFilter == label,
                    onClick = { app.typeFilter = if (app.typeFilter == label) null else label },
                    label = { Text("$label · $n") },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = app.stackByType, onCheckedChange = { app.stackByType = it })
            Text("Apilar por tipo", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { app.stackByType = !app.stackByType })
            Spacer(Modifier.size(12.dp))
            Checkbox(checked = app.exploreDeep, onCheckedChange = { app.exploreDeep = it; app.typeFilter = null })
            Text("Incluir lo de las subcarpetas", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { app.exploreDeep = !app.exploreDeep; app.typeFilter = null })
            Spacer(Modifier.weight(1f))
            var open by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { open = true }) { Text("Ordenar por: ${app.sortBy.label}") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    SortBy.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = { app.sortBy = s; open = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun Crumb(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !current, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun FolderTile(app: AppState, f: FolderInfo) {
    val prot = app.isProtectedDir(f.path)
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val cover = f.cover
    val selected = app.selDirs[f.path] == true
    Column(
        Modifier.padding(4.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .hoverable(src)
            .clickable { if (app.hasSel) app.toggleDir(f.path) else app.navigate(f.path) }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
        ) {
            if (cover != null) {
                val bmp = rememberThumb(cover)
                if (bmp != null) Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.85f)
            }
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = if (cover == null) Modifier.align(Alignment.Center).size(56.dp) else Modifier.align(Alignment.BottomStart).padding(6.dp).size(26.dp),
            )
            if (hovered || app.hasSel) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp).size(26.dp).clip(CircleShape)
                        .background(if (selected) Color.White else Color(0x55000000))
                        .clickable { app.toggleDir(f.path) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (selected) "Quitar selección" else "Seleccionar carpeta",
                        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (prot || hovered) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).clip(CircleShape)
                        .background(if (prot) MaterialTheme.colorScheme.secondaryContainer else Color(0x66000000))
                        .clickable { app.setProtected(f.path, !prot) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (prot) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (prot) "Quitar protección" else "Proteger carpeta",
                        tint = if (prot) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        Text(
            "${f.count} archivos · ${fmtBytes(f.bytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTile(app: AppState, item: Item) {
    val selected = app.sel[item.path] == true
    val t = docType(item.ext)
    Column(
        Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant), RoundedCornerShape(10.dp))
            .combinedClickable(onClick = { app.toggle(item) }, onDoubleClick = { app.openFile(item) })
            .padding(10.dp),
    ) {
        val preview = rememberThumb(item)
        Box(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            if (preview != null) {
                Box(Modifier.fillMaxSize().background(Color.White))
                Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                TypeBadge(item, Modifier.align(Alignment.BottomStart).padding(4.dp))
            } else {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(Color(t.color)), contentAlignment = Alignment.Center) {
                    Text(item.ext.uppercase().take(4).ifEmpty { "—" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopStart).size(20.dp))
            if (item.cloudOnly) Icon(Icons.Outlined.Cloud, contentDescription = "Solo en la nube", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
        }
        Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${fmtBytes(item.size)} · ${shortDate(item.modified)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Fila de chips para saltar entre tipos o grupos sin desplazarse. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipBar(options: List<Pair<String, Int>>, selected: String?, allLabel: String = "Todo", onSelect: (String?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allLabel) })
        options.forEach { (label, n) ->
            FilterChip(selected = selected == label, onClick = { onSelect(if (selected == label) null else label) }, label = { Text("$label · $n") })
        }
    }
}
