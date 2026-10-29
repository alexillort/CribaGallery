package app.criba.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criba.galeria.core.Entry
import app.criba.galeria.core.Graph
import app.criba.galeria.core.Lib
import app.criba.galeria.core.fmtBytes
import coil3.compose.AsyncImage

class ViewerState(val items: List<Entry>, val index: Int)

/** Visor a pantalla completa: desliza entre fotos, pellizca o toca dos veces para hacer zoom. */
@Composable
fun Viewer(lib: Lib, vs: ViewerState, ui: Ui) {
    val live = remember(lib, vs) { vs.items.filter { lib.byId.containsKey(it.id) } }
    if (live.isEmpty()) {
        LaunchedEffect(Unit) { ui.viewer = null }
        return
    }
    val pager = rememberPagerState(initialPage = vs.index.coerceIn(0, live.lastIndex)) { live.size }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { live[it].id }, beyondViewportPageCount = 1) { page ->
            ZoomImage(live[page], isCurrent = pager.settledPage == page, onTap = { chrome = !chrome })
        }
        val e = live[pager.currentPage.coerceIn(0, live.lastIndex)]
        if (chrome) {
            Column(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { ui.viewer = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(fullDate(e.taken), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${pager.currentPage + 1} / ${live.size}", color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(end = 12.dp))
                }
                Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f))) {
                    if (info) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            InfoLine("Carpeta", lib.folderName(e.parentId))
                            InfoLine("Tamaño", fmtBytes(e.size) + if (e.width > 0) " · ${e.width} × ${e.height}" else "")
                            InfoLine("Tipo", e.mime.ifEmpty { "—" })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ViewerAction(Icons.Filled.DriveFileMove, "Mover") { ui.moveIds = listOf(e.id) }
                        ViewerAction(Icons.Filled.Delete, "Eliminar") { ui.confirmDelete = listOf(e.id) }
                        ViewerAction(Icons.Filled.Info, "Info") { info = !info }
                        if (e.webUrl.isNotEmpty()) ViewerAction(Icons.Filled.OpenInNew, "OneDrive") { uri.openUri(e.webUrl) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(k, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall)
        Text(v, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ZoomImage(e: Entry, isCurrent: Boolean, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(isCurrent) {
        if (!isCurrent) { scale = 1f; offset = Offset.Zero }
    }
    val uri = LocalUriHandler.current
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(e.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    },
                )
            }
            .pointerInput(e.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            scale = (scale * ev.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + ev.calculatePan()
                            ev.changes.forEach { it.consume() }
                        } else if (scale > 1f) {
                            offset += ev.calculatePan()
                            ev.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Graph.thumbUrl(e.id, "large"),
            contentDescription = e.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (!e.isVideo && isCurrent) {
            AsyncImage(
                model = Graph.contentUrl(e.id),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (e.isVideo && e.webUrl.isNotEmpty()) {
            IconButton(onClick = { uri.openUri(e.webUrl) }, modifier = Modifier.size(88.dp)) {
                Icon(Icons.Filled.PlayCircle, contentDescription = "Reproducir en OneDrive", tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }
    }
}
