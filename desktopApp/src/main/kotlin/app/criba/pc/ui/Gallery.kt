package app.criba.pc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criba.pc.AppState
import app.criba.pc.Item
import app.criba.pc.Kind
import app.criba.pc.Media
import app.criba.pc.Moment
import app.criba.pc.dayLabel
import app.criba.pc.localDate
import app.criba.pc.monthLabel
import app.criba.pc.timeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Miniaturas en memoria: las más recientes se quedan para que el desplazamiento sea fluido. */
object ThumbMemory {
    private val map = object : LinkedHashMap<String, ImageBitmap>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 900
    }

    @Synchronized
    fun get(path: String): ImageBitmap? = map[path]

    @Synchronized
    fun put(path: String, b: ImageBitmap) {
        map[path] = b
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private val ThumbDispatcher = Dispatchers.IO.limitedParallelism(6)

@Composable
fun rememberThumb(item: Item): ImageBitmap? {
    val state = produceState(ThumbMemory.get(item.path), item.path) {
        if (value == null && item.kind == Kind.PHOTO && !item.cloudOnly) {
            val b = withContext(ThumbDispatcher) { Media.loadThumb(item) }
            if (b != null) ThumbMemory.put(item.path, b)
            value = b
        }
    }
    return state.value
}

sealed interface GRow {
    val key: String
}

class HeaderRow(val title: String, val sub: String?, val group: List<Item>?, override val key: String, val big: Boolean, val year: Int = 0) : GRow
class TileRow(val item: Item, val index: Int, override val key: String) : GRow

fun libraryRows(moments: List<Moment>, query: String, keep: (Item) -> Boolean = { true }): Pair<List<GRow>, List<Item>> {
    val rows = ArrayList<GRow>()
    val flat = ArrayList<Item>()
    var lastMonth = ""
    for (m in moments) {
        val list = m.items.filter { keep(it) && (query.isEmpty() || matches(it, query)) }
        if (list.isEmpty()) continue
        val month = monthLabel(m.start)
        if (month != lastMonth) {
            rows += HeaderRow(month, null, null, "m:$month:${rows.size}", big = true, year = localDate(m.start).year)
            lastMonth = month
        }
        val range = if (localDate(m.start) == localDate(m.end) && m.end - m.start > 60_000) "${timeOf(m.start)}–${timeOf(m.end)}" else timeOf(m.start)
        rows += HeaderRow(dayLabel(m.start), "$range · ${list.size}", list, "d:${m.start}:${rows.size}", big = false)
        for (it in list) {
            rows += TileRow(it, flat.size, it.path)
            flat += it
        }
    }
    return rows to flat
}

fun groupRows(groups: List<Pair<String, List<Item>>>): Pair<List<GRow>, List<Item>> {
    val rows = ArrayList<GRow>()
    val flat = ArrayList<Item>()
    for ((title, list) in groups) {
        if (list.isEmpty()) continue
        if (title.isNotEmpty()) rows += HeaderRow(title, "${list.size}", list, "g:$title:${rows.size}", big = false)
        for (it in list) {
            rows += TileRow(it, flat.size, it.path + "#" + rows.size)
            flat += it
        }
    }
    return rows to flat
}

fun matches(it: Item, q: String): Boolean =
    it.name.lowercase().contains(q) || it.folderName.lowercase().contains(q) || it.camera.lowercase().contains(q)

@Composable
fun MediaGrid(
    app: AppState,
    rows: List<GRow>,
    flat: List<Item>,
    showReasons: Boolean = false,
    state: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
) {
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(app.tileSize.dp),
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
                    is TileRow -> MediaTile(app, row.item, flat, row.index, showReasons)
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

@Composable
fun GridHeader(app: AppState, row: HeaderRow) {
    if (row.big) {
        Text(
            row.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 26.dp, bottom = 2.dp, start = 2.dp),
        )
        return
    }
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val group = row.group
    Row(
        Modifier.fillMaxWidth().hoverable(src).padding(top = 14.dp, bottom = 6.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (group != null && (hovered || app.sel.isNotEmpty())) {
            val all = group.all { app.sel[it.path] == true }
            Icon(
                if (all) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (all) "Quitar selección del grupo" else "Seleccionar el grupo",
                tint = if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable { app.toggleGroup(group) },
            )
        }
        Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (row.sub != null) Text(row.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MediaTile(app: AppState, item: Item, flat: List<Item>, index: Int, showReasons: Boolean) {
    val selected = app.sel[item.path] == true
    val selecting = app.sel.isNotEmpty()
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bmp = rememberThumb(item)
    val reason = if (showReasons) app.report.reasons[item.path] else null
    val best = showReasons && item.path in app.report.best
    Box(
        Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .hoverable(src)
            .clickable { if (selecting) app.toggle(item) else app.openViewer(flat, index) }
    ) {
        val inner = if (selected) Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(8.dp)) else Modifier.fillMaxSize()
        if (bmp != null) {
            Image(bmp, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = inner)
        } else {
            Column(inner.padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    when {
                        item.kind == Kind.VIDEO -> Icons.Filled.Videocam
                        item.cloudOnly -> Icons.Outlined.Cloud
                        item.failed -> Icons.Filled.BrokenImage
                        else -> Icons.Filled.Image
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    item.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (item.kind == Kind.VIDEO && bmp == null) {
            Icon(Icons.Filled.PlayCircle, contentDescription = "Video", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp))
        }
        if (item.cloudOnly && bmp == null && item.kind != Kind.VIDEO) {
            Icon(Icons.Outlined.Cloud, contentDescription = "Solo en la nube", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(16.dp))
        }
        if (reason != null || best) {
            Surface(
                color = if (best) Color(0xFF2D7A58) else Color(0xE6101114),
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    if (best) "Mejor" else reason.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (selecting || hovered) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).clip(CircleShape)
                    .background(if (selected) Color.White else Color(0x33000000))
                    .clickable { app.toggle(item) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (selected) "Quitar selección" else "Seleccionar",
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (selected) {
            Box(Modifier.matchParentSizeCompat().border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(460.dp))
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.size(8.dp))
