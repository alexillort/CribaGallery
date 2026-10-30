package app.criba.pc.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criba.pc.AppState
import app.criba.pc.Item
import app.criba.pc.Kind
import app.criba.pc.Targets
import app.criba.pc.docType
import app.criba.pc.fmtBytes
import java.io.File

private fun n(t: Targets) = if (t.count > 1) " (${t.count})" else ""

/** Menú de clic derecho de un archivo. Si es parte de una selección, aplica a toda la selección. */
fun fileMenu(app: AppState, item: Item, onOpen: () -> Unit): List<ContextMenuItem> {
    val t = app.targetsFor(item)
    val selected = app.sel[item.path] == true
    return buildList {
        add(ContextMenuItem(if (item.isMedia) "Ver" else "Abrir") { onOpen() })
        if (item.isMedia) add(ContextMenuItem("Abrir con la app predeterminada") { app.openFile(item) })
        add(ContextMenuItem(if (selected) "Quitar de la selección" else "Seleccionar") { app.toggle(item) })
        add(ContextMenuItem("Copiar${n(t)}") { app.setClip(t, cut = false) })
        add(ContextMenuItem("Cortar${n(t)}") { app.setClip(t, cut = true) })
        add(ContextMenuItem("Copiar a…${n(t)}") { app.copyTo(t) })
        add(ContextMenuItem("Mover a…${n(t)}") { app.moveTo(t) })
        if (t.count == 1) add(ContextMenuItem("Cambiar nombre…") { app.renaming = item.path })
        add(ContextMenuItem("Mostrar en el Explorador") { app.reveal(item) })
        add(ContextMenuItem("Ir a su carpeta en Criba") { app.navigate(item.parent) })
        add(ContextMenuItem("Copiar ruta") { app.copyPathText(item.path) })
        add(ContextMenuItem("Eliminar${n(t)}") { app.askDelete(t) })
    }
}

fun folderMenu(app: AppState, dir: String): List<ContextMenuItem> {
    val t = app.targetsForDir(dir)
    val selected = app.selDirs[dir] == true
    val prot = app.isProtectedDir(dir)
    return buildList {
        add(ContextMenuItem("Abrir") { app.navigate(dir) })
        add(ContextMenuItem(if (selected) "Quitar de la selección" else "Seleccionar") { app.toggleDir(dir) })
        add(ContextMenuItem("Copiar${n(t)}") { app.setClip(t, cut = false) })
        add(ContextMenuItem("Cortar${n(t)}") { app.setClip(t, cut = true) })
        add(ContextMenuItem("Copiar a…${n(t)}") { app.copyTo(t) })
        add(ContextMenuItem("Mover a…${n(t)}") { app.moveTo(t) })
        if (t.count == 1) add(ContextMenuItem("Cambiar nombre…") { app.renaming = dir })
        add(ContextMenuItem(if (prot) "Quitar protección" else "Proteger carpeta") { app.setProtected(dir, !prot) })
        add(ContextMenuItem("Abrir en el Explorador") { app.openFolderInExplorer(dir) })
        add(ContextMenuItem("Copiar ruta") { app.copyPathText(dir) })
        add(ContextMenuItem("Eliminar${n(t)}") { app.askDelete(t) })
    }
}

/** Clic derecho en un espacio vacío de Explorar. */
fun backgroundMenu(app: AppState, dir: String): List<ContextMenuItem> = buildList {
    val c = app.clip
    if (c != null) add(ContextMenuItem("Pegar aquí (${c.targets.count})") { app.paste(dir) })
    add(ContextMenuItem("Nueva carpeta…") { app.creatingFolder = true })
    add(ContextMenuItem("Seleccionar todo") { app.selectAllVisible() })
    add(ContextMenuItem("Abrir en el Explorador") { app.openFolderInExplorer(dir) })
}

/** Insignia pequeña que dice qué es la miniatura: imagen, video, PDF… */
@Composable
fun TypeBadge(item: Item, modifier: Modifier = Modifier) {
    val color = when (item.kind) {
        Kind.PHOTO -> Color(0xE61F4FA8)
        Kind.VIDEO -> Color(0xE6101114)
        Kind.DOC -> Color(docType(item.ext).color)
    }
    Surface(color = color, shape = RoundedCornerShape(5.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            when (item.kind) {
                Kind.PHOTO -> Icon(Icons.Filled.Image, contentDescription = "Imagen", tint = Color.White, modifier = Modifier.size(13.dp))
                Kind.VIDEO -> Icon(Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(13.dp))
                Kind.DOC -> Text(item.ext.uppercase().take(4), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeleteDialog(app: AppState, t: Targets) {
    val files = t.files.filterNot { app.isProtected(it) }
    val dirs = t.dirs.filterNot { app.dirLocked(it) }
    val skipped = t.count - files.size - dirs.size
    val total = files.size + dirs.size
    val inDirs = remember(dirs) {
        val pre = dirs.map { it + File.separator }
        app.items.filter { x -> pre.any { x.path.startsWith(it) } }
    }
    val bytes = files.sumOf { it.size } + inDirs.sumOf { it.size }
    AlertDialog(
        onDismissRequest = { app.confirmDelete = null },
        title = {
            Text(
                when {
                    total == 0 -> "Está en carpetas protegidas"
                    total == 1 && files.size == 1 -> "¿Eliminar ${files[0].name}?"
                    total == 1 -> "¿Eliminar la carpeta ${File(dirs[0]).name}?"
                    else -> "¿Eliminar $total elementos?"
                }
            )
        },
        text = {
            Text(
                if (total == 0) "Para eliminarlo, primero quita la protección de su carpeta."
                else buildString {
                    if (dirs.isNotEmpty()) append("${dirs.size} carpeta${if (dirs.size == 1) "" else "s"} con ${inDirs.size} archivos. ")
                    append("Ocupan ${fmtBytes(bytes)}. Van a la Papelera de reciclaje, de donde puedes restaurarlos.")
                    if (app.isOneDrive) append(" OneDrive también los manda a su papelera en línea.")
                    if (skipped > 0) append(" $skipped están protegidos y no se tocarán.")
                }
            )
        },
        confirmButton = {
            Button(
                enabled = total > 0,
                onClick = {
                    app.confirmDelete = null
                    app.delete(Targets(files, dirs))
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text("Eliminar") }
        },
        dismissButton = { TextButton(onClick = { app.confirmDelete = null }) { Text("Cancelar") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, action: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val dot = initial.lastIndexOf('.')
    var value by remember {
        mutableStateOf(TextFieldValue(initial, TextRange(0, if (dot > 0) dot else initial.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.widthIn(min = 380.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        },
        confirmButton = { Button(enabled = value.text.isNotBlank(), onClick = { onDone(value.text) }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun FileDialogs(app: AppState) {
    app.confirmDelete?.let { DeleteDialog(app, it) }
    app.renaming?.let { path ->
        NameDialog(
            title = "Cambiar nombre",
            initial = File(path).name,
            action = "Cambiar",
            onDone = { name ->
                app.renaming = null
                app.rename(path, name)
            },
            onDismiss = { app.renaming = null },
        )
    }
    if (app.creatingFolder) {
        NameDialog(
            title = "Nueva carpeta en ${File(app.currentDir()).name}",
            initial = "Nueva carpeta",
            action = "Crear",
            onDone = { name ->
                app.creatingFolder = false
                app.createFolder(app.currentDir(), name)
            },
            onDismiss = { app.creatingFolder = false },
        )
    }
}
