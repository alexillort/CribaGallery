package app.criba.pc.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criba.pc.AppState
import app.criba.pc.Mode
import app.criba.pc.ORDER_DIR
import app.criba.pc.Scheme

@Composable
fun OrganizeView(app: AppState) {
    val photos = app.mode == Mode.PHOTOS
    val schemes = if (photos) listOf(Scheme.YEAR_MONTH, Scheme.DAY, Scheme.SOURCE) else listOf(Scheme.DOC_TYPE, Scheme.DOC_TOPIC, Scheme.DOC_YEAR)
    var scheme by remember(app.mode) { mutableStateOf(schemes[0]) }
    var looseOnly by remember { mutableStateOf(true) }
    var skipMarked by remember { mutableStateOf(true) }
    val plan = remember(app.report, scheme, looseOnly, skipMarked) { app.plan(scheme, looseOnly, skipMarked) }
    val byDir = remember(plan) { plan.groupBy { it.second }.mapValues { it.value.size }.toSortedMap().toList() }
    val rootName = app.root?.name.orEmpty()

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(400.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cómo ordenar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            schemes.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { scheme = s }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = scheme == s, onClick = { scheme = s })
                    Column {
                        Text(s.label, style = MaterialTheme.typography.bodyLarge)
                        Text(s.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Qué archivos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            CheckRow(looseOnly, { looseOnly = it }, "Solo los sueltos en «$rootName»", "Las subcarpetas que ya tienes no se tocan. Desmárcalo para incluirlas.")
            CheckRow(skipMarked, { skipMarked = it }, "Dejar fuera lo sugerido para eliminar", "Así primero limpias y luego ordenas.")
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Se crea la carpeta «$ORDER_DIR» dentro de «$rootName». Nada se borra y puedes deshacerlo." +
                        (if (app.protectedDirs.isNotEmpty()) " Las ${app.protectedDirs.size} carpetas protegidas no se tocan." else "") +
                        if (app.isOneDrive) " Como esta carpeta está en OneDrive, los cambios se sincronizan solos." else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Button(enabled = plan.isNotEmpty() && app.busy == null, onClick = { app.pendingPlan = plan }, modifier = Modifier.fillMaxWidth()) {
                Text(if (plan.isEmpty()) "No hay nada que mover" else "Ordenar ${plan.size} archivos")
            }
            OutlinedButton(enabled = app.canUndo && app.busy == null, onClick = { app.undoOrganize() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Deshacer el último orden")
            }
        }

        OutlinedCard(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.padding(20.dp)) {
                Text("Vista previa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${plan.size} archivos en ${byDir.size} carpetas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (byDir.isEmpty()) {
                    Text(
                        if (looseOnly) "No hay archivos sueltos en la carpeta principal. Desmarca «Solo los sueltos» para incluir subcarpetas." else "Todo está ordenado según esta opción.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(state = listState, contentPadding = PaddingValues(end = 12.dp)) {
                            items(byDir, key = { it.first }) { (dir, n) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        dir.replace("/", "  /  "),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                                    )
                                    Text("$n", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String, note: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }, verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.padding(top = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
