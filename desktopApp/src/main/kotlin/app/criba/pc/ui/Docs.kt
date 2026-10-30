package app.criba.pc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criba.pc.AppState
import app.criba.pc.Item
import app.criba.pc.NO_TOPIC
import app.criba.pc.docType
import app.criba.pc.fmtBytes
import app.criba.pc.shortDate
import app.criba.pc.topicOf

@Composable
fun DocsGrouped(app: AppState, byTopic: Boolean) {
    val r = app.report
    val q = app.query.trim().lowercase()
    val (rows, flat) = remember(r, q, byTopic) {
        val groups = r.docs.filter { q.isEmpty() || matches(it, q) }
            .groupBy { if (byTopic) topicOf(it) else docType(it.ext).label }
            .toList()
            .sortedWith(compareBy<Pair<String, List<Item>>>({ it.first == NO_TOPIC || it.first == "Otros" }, { -it.second.size }))
            .map { (k, v) -> "$k · ${fmtBytes(v.sumOf { it.size })}" to v }
        groupRows(groups)
    }
    SideEffect { app.visibleItems = flat }
    if (flat.isEmpty()) {
        Empty(if (q.isEmpty()) "No hay documentos en esta carpeta." else "Nada coincide con «$q».")
        return
    }
    DocGrid(app, rows)
}

@Composable
fun DocGrid(app: AppState, rows: List<GRow>, showReasons: Boolean = false) {
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 24.dp, bottom = 32.dp),
        ) {
            items(
                rows,
                key = { it.key },
                span = { if (it is TileRow) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
                contentType = { if (it is TileRow) 1 else 0 },
            ) { row ->
                when (row) {
                    is HeaderRow -> GridHeader(app, row)
                    is TileRow -> DocTile(app, row.item, showReasons)
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocTile(app: AppState, item: Item, showReasons: Boolean) {
    val selected = app.sel[item.path] == true
    val t = docType(item.ext)
    val reason = if (showReasons) app.report.reasons[item.path] else null
    val best = showReasons && item.path in app.report.best
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = { app.toggle(item) }, onDoubleClick = { app.openFile(item) }),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(t.color)),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.ext.uppercase().take(4).ifEmpty { "—" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${fmtBytes(item.size)} · ${shortDate(item.modified)} · ${item.folderName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (best) Text("Se conserva", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2D7A58))
                else if (reason != null) Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            if (item.cloudOnly) Icon(Icons.Outlined.Cloud, contentDescription = "Solo en la nube", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
