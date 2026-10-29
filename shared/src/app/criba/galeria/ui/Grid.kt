package app.criba.galeria.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.criba.galeria.core.Entry
import app.criba.galeria.core.Graph
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface GridRow { val key: String }
class DayRow(val label: String, val ids: List<String>, override val key: String) : GridRow
class CellRow(val e: Entry, val index: Int) : GridRow { override val key: String get() = e.id }

fun buildRows(items: List<Entry>, byDay: Boolean): List<GridRow> {
    if (!byDay) return items.mapIndexed { i, e -> CellRow(e, i) }
    val out = ArrayList<GridRow>(items.size + items.size / 8)
    var i = 0
    while (i < items.size) {
        val d = localDate(items[i].taken)
        var j = i
        while (j < items.size && localDate(items[j].taken) == d) j++
        out.add(DayRow(dayLabel(d), items.subList(i, j).map { it.id }, "d:$d:$i"))
        for (k in i until j) out.add(CellRow(items[k], k))
        i = j
    }
    return out
}

val COLUMN_STEPS = listOf(2, 3, 4, 5, 6, 8, 10, 12)

/** Cuadrícula de fotos estilo galería: encabezados por día, pellizco para zoom y barra de desplazamiento rápido. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(
    items: List<Entry>,
    ui: Ui,
    gridThumb: String,
    byDay: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    onOpen: (Int) -> Unit,
) {
    val rows = remember(items, byDay) { buildRows(items, byDay) }
    val state = rememberLazyGridState()
    val thumb = if (ui.columns <= 2) "large" else gridThumb
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zoom = 1f
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = ev.changes.count { it.pressed }
                    if (pressed == 0) break
                    if (pressed >= 2) {
                        zoom *= ev.calculateZoom()
                        if (zoom > 1.3f) { ui.zoomIn(); zoom = 1f }
                        if (zoom < 0.77f) { ui.zoomOut(); zoom = 1f }
                        ev.changes.forEach { it.consume() }
                    }
                }
            }
        }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(ui.columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (header != null) {
                item(key = "__header", span = { GridItemSpan(maxLineSpan) }) { header() }
            }
            items(
                rows,
                key = { it.key },
                span = { if (it is DayRow) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { if (it is DayRow) 0 else 1 },
            ) { row ->
                when (row) {
                    is DayRow -> DayHeader(row, ui)
                    is CellRow -> Cell(row.e, ui, thumb) { onOpen(row.index) }
                }
            }
        }
        FastScroller(state, rows, if (header != null) 1 else 0)
    }
}

@Composable
private fun DayHeader(row: DayRow, ui: Ui) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (ui.selecting) {
            val all = row.ids.all { ui.sel[it] == true }
            Icon(
                if (all) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (all) "Quitar selección del día" else "Seleccionar el día",
                tint = if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).clickable {
                    if (all) row.ids.forEach { ui.sel.remove(it) } else row.ids.forEach { ui.sel[it] = true }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Cell(e: Entry, ui: Ui, thumb: String, onOpen: () -> Unit) {
    val selected = ui.sel[e.id] == true
    Box(
        Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = { if (ui.selecting) ui.toggle(e.id) else onOpen() },
                onLongClick = { ui.selectMode = true; ui.toggle(e.id) },
            )
    ) {
        AsyncImage(
            model = Graph.thumbUrl(e.id, thumb),
            contentDescription = e.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (selected) Modifier.padding(10.dp).clip(RoundedCornerShape(10.dp)) else Modifier),
        )
        if (e.isVideo) {
            Icon(
                Icons.Filled.PlayCircle, contentDescription = "Video", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp),
            )
        }
        if (ui.selecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(22.dp),
            )
        }
    }
}

/** Arrastra el borde derecho para saltar entre meses. */
@Composable
private fun BoxScope.FastScroller(state: LazyGridState, rows: List<GridRow>, offset: Int) {
    if (rows.size < 150) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val frac by remember(rows) {
        derivedStateOf { (state.firstVisibleItemIndex - offset).coerceAtLeast(0).toFloat() / rows.size }
    }
    val thumbPx = with(density) { 48.dp.toPx() }

    fun jump() {
        if (height <= 0) return
        val idx = (dragY / height * rows.size).toInt().coerceIn(0, rows.lastIndex)
        scope.launch { state.scrollToItem(idx + offset) }
    }

    val y = if (dragging) dragY else frac * height
    val thumbTop = (y - thumbPx / 2).coerceIn(0f, (height - thumbPx).coerceAtLeast(0f))

    Box(
        Modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { height = it.height }
            .pointerInput(rows) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true; dragY = it.y; jump() },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    dragY = change.position.y.coerceIn(0f, height.toFloat())
                    jump()
                }
            }
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbTop.roundToInt()) }
                .padding(end = 3.dp)
                .size(width = if (dragging) 8.dp else 5.dp, height = 48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 1f else 0.55f))
        )
    }
    if (dragging) {
        val idx = (dragY / height.coerceAtLeast(1) * rows.size).toInt().coerceIn(0, rows.lastIndex)
        val label = rows.subList(idx, rows.size).firstNotNullOfOrNull { (it as? CellRow)?.e }?.let { monthLabel(it.taken) }
        if (label != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-with(density) { 44.dp.roundToPx() }, thumbTop.roundToInt()) },
            ) {
                Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
