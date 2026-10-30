package app.criba.pc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criba.pc.AppState
import app.criba.pc.Item
import app.criba.pc.Kind
import app.criba.pc.Mode
import app.criba.pc.Report
import app.criba.pc.Review
import app.criba.pc.docType
import app.criba.pc.fmtBytes

class CleanCat(
    val title: String,
    val note: String,
    val groups: List<List<Item>>,
    val grouped: Boolean,
    /** Lo que ya va marcado al abrir la revisión. */
    val preselect: Set<String>,
) {
    val all: List<Item> = groups.flatten()
    val count: Int get() = if (grouped) preselect.size else all.size
    val bytes: Long = if (preselect.isNotEmpty()) all.filter { it.path in preselect }.sumOf { it.size } else all.sumOf { it.size }
}

private fun extras(groups: List<List<Item>>) = groups.flatMap { it.drop(1) }.map { it.path }.toSet()
private fun allOf(list: List<Item>) = list.map { it.path }.toSet()

fun cleanCats(r: Report, mode: Mode): List<CleanCat> = if (mode == Mode.PHOTOS) listOf(
    CleanCat("Duplicados exactos", "El mismo archivo guardado más de una vez. Se conserva una copia de cada uno.", r.photoDups, true, extras(r.photoDups)),
    CleanCat("Fotos casi iguales", "Ráfagas y tomas repetidas. Se conserva la más nítida de cada grupo.", r.similar, true, extras(r.similar)),
    CleanCat("Borrosas", "Fotos movidas o desenfocadas.", listOf(r.blurry), false, allOf(r.blurry)),
    CleanCat("Muy oscuras o quemadas", "Fotos casi negras o casi blancas, muchas veces tomadas sin querer.", listOf(r.darkOrBright), false, allOf(r.darkOrBright)),
    CleanCat("Capturas de pantalla", "Revisa las que ya no necesitas.", listOf(r.screenshots), false, emptySet()),
    CleanCat("WhatsApp", "Fotos y videos recibidos por WhatsApp.", listOf(r.whatsapp), false, emptySet()),
    CleanCat("Videos pesados", "Videos de más de 300 MB, del más pesado al más ligero.", listOf(r.bigVideos), false, emptySet()),
) else listOf(
    CleanCat("Duplicados exactos", "El mismo archivo guardado más de una vez. Se conserva una copia.", r.docDups, true, extras(r.docDups)),
    CleanCat("Versiones anteriores", "final, final2, v1, v2, (1)… Se conserva la más reciente de cada documento.", r.versions, true, extras(r.versions)),
    CleanCat("Temporales", "Archivos que dejan Office y los navegadores: ~\$, .tmp, descargas incompletas.", listOf(r.temp), false, allOf(r.temp)),
    CleanCat("Instaladores", "Programas que ya instalaste (.exe, .msi, .iso…).", listOf(r.installers), false, allOf(r.installers)),
    CleanCat("Archivos pesados", "De más de 200 MB.", listOf(r.bigDocs), false, emptySet()),
    CleanCat("Sin cambios en 3 años", "Documentos que no se modifican hace mucho. Útil para archivar.", listOf(r.oldDocs), false, emptySet()),
)

@Composable
fun CleanView(app: AppState) {
    val r = app.report
    val cats = remember(r, app.mode) { cleanCats(r, app.mode) }
    val suggested = remember(r, app.mode) {
        val pool = if (app.mode == Mode.PHOTOS) r.media else r.docs
        pool.filter { it.path in r.reasons }
    }
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(440.dp),
            state = state,
            contentPadding = PaddingValues(start = 24.dp, end = 28.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (suggested.isEmpty()) "No encontré nada claro para eliminar" else "Puedes liberar ${fmtBytes(suggested.sumOf { it.size })}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                if (app.analyzing) "Sigo analizando fotos; esta cifra va a crecer." else "${suggested.size} archivos sugeridos. Revisa cada grupo antes de eliminar: todo va a la Papelera de reciclaje.",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (suggested.isNotEmpty()) {
                            Button(onClick = {
                                app.openReview(Review("Todo lo sugerido", "Ya marqué todo lo sugerido. Quita lo que quieras conservar.", listOf(suggested), false), allOf(suggested))
                            }) { Text("Revisar todo") }
                        }
                    }
                }
            }
            items(cats, key = { it.title }) { c -> CleanCard(app, c) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun CleanCard(app: AppState, c: CleanCat) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (c.all.isEmpty()) "Nada por aquí" else if (c.grouped) "${c.count} sobrantes en ${c.groups.size} grupos · ${fmtBytes(c.bytes)}" else "${c.all.size} archivos · ${fmtBytes(c.bytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (c.preselect.isNotEmpty() && c.all.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    enabled = c.all.isNotEmpty(),
                    onClick = { app.openReview(Review(c.title, c.note, c.groups, c.grouped), c.preselect) },
                ) { Text("Revisar") }
            }
            Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.all.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.all.take(7).forEach { Preview(it) }
                }
            }
        }
    }
}

@Composable
private fun Preview(it: Item) {
    if (it.kind == Kind.DOC) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color(docType(it.ext).color)), contentAlignment = Alignment.Center) {
            Text(it.ext.uppercase().take(4), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    val bmp = rememberThumb(it)
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bmp != null) Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun ReviewView(app: AppState, review: Review) {
    val r = app.report
    val (rows, flat) = remember(review, r) {
        val byPath = HashMap<String, Item>()
        r.media.forEach { byPath[it.path] = it }
        r.docs.forEach { byPath[it.path] = it }
        val live = review.groups.map { g -> g.mapNotNull { byPath[it.path] } }
            .filter { it.isNotEmpty() && (!review.grouped || it.size > 1) }
        val titled = live.map { g ->
            val title = if (!review.grouped) "" else if (g[0].isMedia) "${g.size} parecidas" else "${g.size} versiones de ${g[0].name}"
            title to g
        }
        groupRows(titled)
    }
    SideEffect { app.visibleItems = flat }
    val selected = app.sel.keys.size
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(review.note, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (flat.firstOrNull()?.isMedia == true) "Haz clic en una foto para marcarla o desmarcarla." else "Haz clic para marcar o desmarcar. Doble clic abre el archivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { app.review = null; app.sel.clear() }) { Text("Volver") }
                Button(
                    enabled = selected > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    onClick = { app.confirmTrash = app.selectedItems() },
                ) { Text(if (selected > 0) "Eliminar $selected" else "Nada marcado") }
            }
        }
        if (flat.isEmpty()) {
            Empty("Listo, ya no queda nada en este grupo.")
            return@Column
        }
        Spacer(Modifier.size(4.dp))
        if (flat.first().isMedia) MediaGrid(app, rows, flat, showReasons = true) else DocGrid(app, rows, showReasons = true)
    }
}
