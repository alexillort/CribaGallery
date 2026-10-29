package app.criba.galeria.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criba.galeria.core.Lib

@Composable
fun DeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "¿Eliminar este elemento?" else "¿Eliminar $count elementos?") },
        text = { Text("Irán a la papelera de OneDrive. Puedes recuperarlos desde onedrive.com durante 30 días.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Elige carpeta destino navegando el árbol de OneDrive; permite crear una nueva. */
@Composable
fun MoveDialog(
    lib: Lib,
    count: Int,
    onMove: (folderId: String) -> Unit,
    onCreate: (parentId: String, name: String, then: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var cur by remember { mutableStateOf(lib.rootId) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val subs = lib.foldersByParent[cur ?: ""].orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Mover a…" else "Mover $count a…") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val parent = cur?.let { lib.folders[it]?.parentId }
                    if (cur != null && cur != lib.rootId && parent != null) {
                        IconButton(onClick = { cur = parent }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Carpeta anterior")
                        }
                    }
                    Text(
                        lib.folderName(cur),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                if (lib.rootId == null) {
                    Text("Aún se está leyendo tu OneDrive. Intenta de nuevo en un momento.")
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(subs, key = { it.id }) { f ->
                        ListItem(
                            headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            modifier = Modifier.clickable { cur = f.id },
                        )
                    }
                }
                if (naming) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre de la carpeta nueva") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cur != null && (!naming || name.isNotBlank()),
                onClick = {
                    val target = cur ?: return@TextButton
                    if (naming) {
                        onCreate(target, name.trim()) { newId -> cur = newId }
                        naming = false
                        name = ""
                    } else {
                        onMove(target)
                    }
                },
            ) { Text(if (naming) "Crear" else "Mover aquí") }
        },
        dismissButton = {
            Row {
                if (!naming) TextButton(onClick = { naming = true }) { Text("Nueva carpeta") }
                TextButton(onClick = { if (naming) naming = false else onDismiss() }) { Text("Cancelar") }
            }
        },
    )
}
