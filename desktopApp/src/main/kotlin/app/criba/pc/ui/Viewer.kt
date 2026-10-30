package app.criba.pc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criba.pc.AppState
import app.criba.pc.Kind
import app.criba.pc.Media
import app.criba.pc.ViewerState
import app.criba.pc.fmtBytes
import app.criba.pc.fullDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val OnDark = Color(0xFFECEFF3)
private val OnDarkMuted = Color(0xFFA9B0BA)

/** Visor a pantalla completa. Flechas del teclado para avanzar, Supr para eliminar, Esc para cerrar. */
@Composable
fun Viewer(app: AppState, vs: ViewerState) {
    if (vs.list.isEmpty()) {
        LaunchedEffect(Unit) { app.viewer = null }
        return
    }
    val i = vs.index.coerceIn(0, vs.list.lastIndex)
    val item = vs.list[i]
    val thumb = rememberThumb(item)
    val full by produceState<ImageBitmap?>(null, item.path) {
        value = null
        if (item.kind == Kind.PHOTO && !item.cloudOnly) value = withContext(Dispatchers.IO) { runCatching { Media.loadFull(item, 2800) }.getOrNull() }
    }
    // Precarga la siguiente miniatura.
    vs.list.getOrNull(i + 1)?.let { rememberThumb(it) }

    Box(
        Modifier.fillMaxSize().background(Color(0xF20D0F12))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { app.viewer = null }) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = OnDark) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(item.name, color = OnDark, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${fullDate(item.taken)} · fecha de ${item.dateSource}", color = OnDarkMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text("${i + 1} de ${vs.list.size}", color = OnDarkMuted, modifier = Modifier.padding(end = 12.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val img = full ?: thumb
                if (img != null) {
                    Image(img, contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 8.dp))
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            when {
                                item.kind == Kind.VIDEO -> Icons.Filled.Videocam
                                item.cloudOnly -> Icons.Outlined.Cloud
                                else -> Icons.Filled.BrokenImage
                            },
                            contentDescription = null,
                            tint = OnDarkMuted,
                            modifier = Modifier.size(72.dp),
                        )
                        Text(
                            when {
                                item.kind == Kind.VIDEO -> "Video"
                                item.cloudOnly -> "Este archivo está solo en OneDrive. Ábrelo para descargarlo."
                                item.ext == "heic" || item.ext == "heif" -> "Windows necesita la extensión HEIF para mostrar esta foto aquí."
                                else -> "No se pudo mostrar esta imagen."
                            },
                            color = OnDarkMuted,
                        )
                        Button(onClick = { app.openFile(item) }) { Text(if (item.kind == Kind.VIDEO) "Reproducir" else "Abrir") }
                    }
                }
                if (i > 0) {
                    NavButton(Modifier.align(Alignment.CenterStart).padding(start = 12.dp), true) { vs.index = i - 1 }
                }
                if (i < vs.list.lastIndex) {
                    NavButton(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), false) { vs.index = i + 1 }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    val dims = if (item.width > 0) " · ${item.width} × ${item.height}" else ""
                    Text("${fmtBytes(item.size)}$dims${if (item.camera.isNotEmpty()) " · " + item.camera else ""}", color = OnDark, style = MaterialTheme.typography.bodySmall)
                    Text(item.parent, color = OnDarkMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    app.report.reasons[item.path]?.let {
                        Text("Sugerida para eliminar: $it", color = Color(0xFFF2B8B5), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
                TextButton(onClick = { app.reveal(item) }) { Text("Mostrar en carpeta", color = OnDark) }
                TextButton(onClick = { app.openFile(item) }) { Text("Abrir con…", color = OnDark) }
                OutlinedButton(onClick = { app.chooseAndMove(listOf(item)) }) { Text("Mover a…", color = OnDark) }
                Button(
                    onClick = { app.confirmTrash = listOf(item) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Eliminar")
                }
            }
        }
    }
}

@Composable
private fun NavButton(modifier: Modifier, left: Boolean, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(Color(0x55FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (left) "Anterior" else "Siguiente",
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}
