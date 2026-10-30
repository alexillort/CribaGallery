package app.criba.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JFileChooser

enum class Mode { PHOTOS, DOCS }

enum class Section(val label: String) {
    LIBRARY("Biblioteca"), EXPLORE("Explorar"), TYPES("Por tipo"), TOPICS("Por tema"), CLEAN("Limpiar"), ORGANIZE("Ordenar")
}

enum class SortBy(val label: String) { NAME("Nombre"), DATE("Fecha"), SIZE("Tamaño") }

enum class Scheme(val label: String, val desc: String) {
    YEAR_MONTH("Año y mes", "Ordenado / 2025 / 2025-06 junio"),
    DAY("Por día", "Ordenado / 2025 / 2025-06-14"),
    SOURCE("Por origen", "Cámara, Capturas de pantalla, WhatsApp, Videos"),
    DOC_TYPE("Por tipo", "Ordenado / PDF, Hojas de cálculo, Presentaciones…"),
    DOC_TOPIC("Por tema", "Ordenado / Facturas y recibos, Académico, Contratos…"),
    DOC_YEAR("Año y tipo", "Ordenado / 2024 / PDF"),
}

/** Lo que una acción afecta: archivos sueltos y carpetas completas. */
class Targets(val files: List<Item>, val dirs: List<String> = emptyList()) {
    val count: Int get() = files.size + dirs.size
    val isEmpty: Boolean get() = count == 0
}

class Clip(val targets: Targets, val cut: Boolean)

class Review(val title: String, val note: String, val groups: List<List<Item>>, val grouped: Boolean)

class ViewerState(items: List<Item>, start: Int) {
    val list = mutableStateListOf<Item>().also { it.addAll(items) }
    var index by mutableIntStateOf(start)

    fun drop(paths: Set<String>) {
        list.removeAll { it.path in paths }
        index = index.coerceAtMost(list.lastIndex).coerceAtLeast(0)
    }

    fun replace(moved: Map<String, Item>) {
        for (k in list.indices) moved[list[k].path]?.let { list[k] = it }
    }
}

class AppState(val dataDir: File) {
    val scope = MainScope()
    private val prefsFile = File(dataDir, "ajustes.properties")
    private val prefs = Properties()

    var root by mutableStateOf<File?>(null)
        private set
    private var lib: Library? = null
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var report by mutableStateOf(Report.EMPTY)
        private set

    var scanning by mutableStateOf(false)
        private set
    var scanCount by mutableIntStateOf(0)
        private set
    var analyzing by mutableStateOf(false)
        private set
    var analyzedCount by mutableIntStateOf(0)
        private set
    var analyzeTotal by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set

    var mode by mutableStateOf(Mode.PHOTOS)
    var section by mutableStateOf(Section.LIBRARY)
    /** Carpeta abierta en Explorar (null = la principal). */
    var exploreDir by mutableStateOf<String?>(null)
    var exploreDeep by mutableStateOf(false)
    var stackByType by mutableStateOf(false)
    var typeFilter by mutableStateOf<String?>(null)
    var sortBy by mutableStateOf(SortBy.NAME)
    var showProtected by mutableStateOf(false)

    /** Carpetas protegidas: nada de adentro se sugiere, se elimina ni se ordena. */
    var protectedDirs by mutableStateOf<Set<String>>(emptySet())
        private set
    private var autoDirs: Set<String> = emptySet()
    var review by mutableStateOf<Review?>(null)
    var query by mutableStateOf("")
    var tileSize by mutableFloatStateOf(170f)
    val sel = mutableStateMapOf<String, Boolean>()
    var viewer by mutableStateOf<ViewerState?>(null)
    val selDirs = mutableStateMapOf<String, Boolean>()
    var confirmDelete by mutableStateOf<Targets?>(null)
    var clip by mutableStateOf<Clip?>(null)
    /** Ruta del archivo o carpeta que se está renombrando. */
    var renaming by mutableStateOf<String?>(null)
    var creatingFolder by mutableStateOf(false)
    /** Sube cada vez que cambian carpetas en disco, para que Explorar se recalcule. */
    var fsVersion by mutableIntStateOf(0)
        private set
    var pendingPlan by mutableStateOf<List<Pair<Item, String>>?>(null)

    /** Lo que se ve ahora en pantalla (para "Seleccionar todo"). */
    var visibleItems: List<Item> = emptyList()

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var job: Job? = null

    init {
        dataDir.mkdirs()
        Media.thumbDir = File(dataDir, "miniaturas").also { it.mkdirs() }
        runCatching { prefsFile.inputStream().use { prefs.load(it) } }
        prefs.getProperty("tile")?.toFloatOrNull()?.let { tileSize = it }
    }

    private fun savePref(k: String, v: String) {
        prefs.setProperty(k, v)
        runCatching { prefsFile.outputStream().use { prefs.store(it, "Criba") } }
    }

    fun start() {
        val last = prefs.getProperty("root")?.let { File(it) }
        if (last != null && last.isDirectory && root == null) open(last)
    }

    val isOneDrive: Boolean
        get() {
            val r = root?.path?.lowercase() ?: return false
            return listOf("OneDrive", "OneDriveConsumer", "OneDriveCommercial").mapNotNull { System.getenv(it) }
                .any { r.startsWith(it.lowercase()) } || r.contains("onedrive")
        }

    fun open(dir: File) {
        job?.cancel()
        val l = Library(dir, dataDir)
        lib = l
        root = l.root
        savePref("root", l.root.path)
        items = emptyList()
        report = Report.EMPTY
        clearSel()
        viewer = null
        review = null
        exploreDir = null
        typeFilter = null
        query = ""
        section = if (mode == Mode.PHOTOS) Section.LIBRARY else Section.TYPES
        canUndo = l.undoFile.exists()
        autoDirs = emptySet()
        recomputeProtected()
        job = scope.launch { refresh(l) }
    }

    fun rescan() {
        root?.let { open(it) }
    }

    fun closeFolder() {
        job?.cancel()
        lib = null
        root = null
        items = emptyList()
        report = Report.EMPTY
        prefs.remove("root")
        runCatching { prefsFile.outputStream().use { prefs.store(it, "Criba") } }
    }

    private suspend fun refresh(l: Library) {
        scanning = true
        scanCount = 0
        val counter = AtomicInteger()
        val ticker = scope.launch {
            while (isActive) {
                scanCount = counter.get()
                delay(200)
            }
        }
        val list = try {
            withContext(Dispatchers.IO) { l.scan(l.loadIndex(), counter) }
        } finally {
            ticker.cancel()
        }
        items = list
        scanCount = list.size
        scanning = false
        autoDirs = HashSet(l.projectDirs)
        recomputeProtected()
        val prot0 = protectedDirs
        report = withContext(Dispatchers.Default) { Report.build(list, prot0) }

        val todo = list.filter { it.isMedia && !it.analyzed }
        analyzeTotal = todo.size
        analyzedCount = 0
        analyzing = true
        val done = AtomicInteger()
        val ticker2 = scope.launch {
            var last = 0
            while (isActive) {
                delay(400)
                analyzedCount = done.get()
                if (done.get() - last >= 500) {
                    last = done.get()
                    val snap = items
                    val prot = protectedDirs
                    report = withContext(Dispatchers.Default) { Report.build(snap, prot) }
                }
            }
        }
        try {
            withContext(Dispatchers.IO) {
                val q = ConcurrentLinkedQueue(todo)
                val n = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 8)
                coroutineScope {
                    repeat(n) {
                        launch {
                            while (true) {
                                ensureActive()
                                val x = q.poll() ?: break
                                Media.analyze(x)
                                done.incrementAndGet()
                            }
                        }
                    }
                }
                l.hashDuplicates(list)
            }
        } finally {
            ticker2.cancel()
            analyzing = false
        }
        analyzedCount = done.get()
        val snap = items
        val prot = protectedDirs
        report = withContext(Dispatchers.Default) { Report.build(snap, prot) }
        withContext(Dispatchers.IO) { l.saveIndex(snap) }
    }

    // ---------- selección y navegación ----------

    fun selectedItems(): List<Item> = items.filter { sel[it.path] == true }

    val hasSel: Boolean get() = sel.isNotEmpty() || selDirs.isNotEmpty()
    val selCount: Int get() = sel.size + selDirs.size

    fun clearSel() {
        sel.clear()
        selDirs.clear()
    }

    fun selection(): Targets = Targets(selectedItems(), selDirs.keys.toList())

    fun toggleDir(dir: String) {
        if (selDirs[dir] == true) selDirs.remove(dir) else selDirs[dir] = true
    }

    /** Si el elemento ya está en una selección de varios, la acción aplica a toda la selección. */
    fun targetsFor(it: Item): Targets = if (sel[it.path] == true && selCount > 1) selection() else Targets(listOf(it))
    fun targetsForDir(dir: String): Targets = if (selDirs[dir] == true && selCount > 1) selection() else Targets(emptyList(), listOf(dir))

    fun askDelete(files: List<Item>) {
        confirmDelete = Targets(files)
    }

    fun askDelete(t: Targets) {
        confirmDelete = t
    }

    /** Una carpeta no se toca si está protegida o tiene adentro algo protegido. */
    fun dirLocked(dir: String): Boolean = isProtectedDir(dir) || protectedDirs.any { it.startsWith(dir + File.separator) }

    fun toggle(it: Item) {
        if (sel[it.path] == true) sel.remove(it.path) else sel[it.path] = true
    }

    fun toggleGroup(list: List<Item>) {
        if (list.all { sel[it.path] == true }) list.forEach { sel.remove(it.path) } else list.forEach { sel[it.path] = true }
    }

    fun selectAllVisible() {
        visibleItems.forEach { sel[it.path] = true }
    }

    fun switchMode(m: Mode) {
        if (mode == m) return
        mode = m
        clearSel()
        review = null
        typeFilter = null
        query = ""
        section = if (m == Mode.PHOTOS) Section.LIBRARY else Section.TYPES
    }

    fun go(s: Section) {
        section = s
        review = null
        typeFilter = null
        clearSel()
    }

    fun setTile(v: Float) {
        tileSize = v
        savePref("tile", v.toInt().toString())
    }

    fun openViewer(list: List<Item>, index: Int) {
        viewer = ViewerState(list, index)
    }

    fun openReview(r: Review, preselect: Set<String>) {
        clearSel()
        preselect.forEach { sel[it] = true }
        review = r
    }

    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        if (confirmDelete != null || pendingPlan != null || renaming != null || creatingFolder) return false
        val v = viewer
        if (v != null) {
            when (e.key) {
                Key.DirectionLeft -> { if (v.index > 0) v.index--; return true }
                Key.DirectionRight -> { if (v.index < v.list.lastIndex) v.index++; return true }
                Key.Escape -> { viewer = null; return true }
                Key.Delete -> { v.list.getOrNull(v.index)?.let { askDelete(listOf(it)) }; return true }
            }
            return false
        }
        if (e.key == Key.Escape && hasSel) {
            clearSel()
            return true
        }
        if (e.isCtrlPressed && section == Section.EXPLORE && review == null) {
            when (e.key) {
                Key.C -> if (hasSel) { setClip(selection(), cut = false); return true }
                Key.X -> if (hasSel) { setClip(selection(), cut = true); return true }
                Key.V -> if (clip != null) { paste(currentDir()); return true }
            }
        }
        if (e.key == Key.Delete && hasSel && query.isEmpty()) {
            askDelete(selection())
            return true
        }
        if (e.key == Key.F2 && selCount == 1) {
            renaming = sel.keys.firstOrNull() ?: selDirs.keys.firstOrNull()
            return true
        }
        return false
    }

    // ---------- carpetas protegidas ----------

    private fun prefSet(name: String): Set<String> {
        val l = lib ?: return emptySet()
        return prefs.getProperty("$name.${l.prefKey}")?.split('|')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    private fun savePrefSet(name: String, v: Set<String>) {
        val l = lib ?: return
        savePref("$name.${l.prefKey}", v.joinToString("|"))
    }

    private fun recomputeProtected() {
        protectedDirs = (autoDirs + prefSet("proteger")) - prefSet("desproteger")
    }

    fun isAutoProtected(dir: String) = dir in autoDirs

    fun isProtectedDir(dir: String): Boolean = protectedDirs.any { dir == it || dir.startsWith(it + File.separator) }

    fun isProtected(it: Item): Boolean = protectedDirs.any { p -> it.path.startsWith(p + File.separator) }

    fun setProtected(dir: String, on: Boolean) {
        var add = prefSet("proteger")
        var off = prefSet("desproteger")
        if (on) {
            add = add + dir
            off = off - dir
        } else {
            add = add - dir
            if (dir in autoDirs) off = off + dir
            // Quitar la protección de una carpeta también la quita de las que la contienen.
            protectedDirs.filter { dir.startsWith(it + File.separator) }.forEach { parent ->
                add = add - parent
                if (parent in autoDirs) off = off + parent
            }
        }
        savePrefSet("proteger", add)
        savePrefSet("desproteger", off)
        recomputeProtected()
        clearSel()
        scope.launch { rebuild() }
        say(if (on) "Carpeta protegida: nada de adentro se eliminará ni se ordenará" else "Carpeta sin protección")
    }

    fun chooseAndProtect() {
        val r = root ?: return
        val f = chooseFolder("Elige la carpeta que quieres proteger", r) ?: return
        val p = f.absoluteFile.path
        if (!p.startsWith(r.path + File.separator)) {
            say("Elige una carpeta dentro de ${r.name}")
            return
        }
        setProtected(p, true)
    }

    // ---------- explorar ----------

    fun navigate(dir: String?) {
        exploreDir = if (dir == root?.path) null else dir
        typeFilter = null
        clearSel()
        if (section != Section.EXPLORE) {
            review = null
            section = Section.EXPLORE
        }
    }

    fun openFolderInExplorer(dir: String) {
        try {
            if (IS_WINDOWS) ProcessBuilder("explorer.exe", dir).start() else Desktop.getDesktop().open(File(dir))
        } catch (_: Exception) {
            say("No se pudo abrir la carpeta")
        }
    }

    // ---------- operaciones ----------

    fun chooseRoot() {
        chooseFolder("Elige la carpeta que quieres ordenar", root ?: File(System.getProperty("user.home")))?.let { open(it) }
    }

    fun chooseAndMove(list: List<Item>) = moveTo(Targets(list))

    fun currentDir(): String = exploreDir ?: root?.path.orEmpty()

    fun moveTo(t: Targets) {
        val dest = chooseFolder("Mover ${t.count} a…", File(currentDir())) ?: return
        transfer(t, dest, copy = false)
    }

    fun copyTo(t: Targets) {
        val dest = chooseFolder("Copiar ${t.count} a…", File(currentDir())) ?: return
        transfer(t, dest, copy = true)
    }

    fun setClip(t: Targets, cut: Boolean) {
        if (t.isEmpty) return
        clip = Clip(t, cut)
        val files = t.files.map { it.file } + t.dirs.map { File(it) }
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(FileListTransfer(files), null)
        }
        say(
            (if (cut) "Cortado" else "Copiado") + ": ${t.count} elemento${if (t.count == 1) "" else "s"}. " +
                "Ve a otra carpeta y usa Pegar aquí" + if (!cut) " (también puedes pegarlos en el Explorador de Windows)." else "."
        )
    }

    fun paste(destDir: String) {
        val c = clip ?: return
        transfer(c.targets, File(destDir), copy = !c.cut)
        if (c.cut) clip = null
    }

    fun copyPathText(path: String) {
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(path), null)
            say("Ruta copiada")
        }
    }

    /** Mueve o copia archivos y carpetas a un destino, y actualiza la vista sin volver a escanear. */
    fun transfer(t: Targets, dest: File, copy: Boolean) {
        val r = root ?: return
        val blockedFiles = if (copy) emptyList() else t.files.filter { isProtected(it) }
        val blockedDirs = if (copy) emptyList() else t.dirs.filter { dirLocked(it) }
        val files = t.files - blockedFiles.toSet()
        val dirs = t.dirs - blockedDirs.toSet()
        if (files.isEmpty() && dirs.isEmpty()) {
            say("Están en carpetas protegidas: no se movieron")
            return
        }
        val verb = if (copy) "Copiando" else "Moviendo"
        work("$verb ${files.size + dirs.size}…") {
            val prefix = r.path + File.separator
            val inside = dest.absoluteFile.path == r.path || dest.absoluteFile.path.startsWith(prefix)
            var ok = 0
            val moved = HashMap<String, Item>()
            val added = ArrayList<Item>()
            val dirMoves = ArrayList<Pair<String, String>>()
            withContext(Dispatchers.IO) {
                for (it in files) {
                    if (copy) {
                        copyFileItem(it, dest)?.let { n -> ok++; if (inside) added += n }
                    } else {
                        val m = lib?.move(listOf(it), dest).orEmpty()
                        if (m.isNotEmpty()) { ok++; moved.putAll(m) }
                    }
                }
                val snapshot = items
                for (d in dirs) {
                    val nd = transferDir(d, dest, copy) ?: continue
                    ok++
                    val old = d + File.separator
                    for (x in snapshot) {
                        if (!x.path.startsWith(old)) continue
                        val n = x.movedTo(nd + File.separator + x.path.substring(old.length))
                        if (copy) {
                            runCatching { java.nio.file.Files.copy(Media.thumbFile(x).toPath(), Media.thumbFile(n).toPath()) }
                            if (inside) added += n
                        } else {
                            Media.moveThumb(x, n)
                            moved[x.path] = n
                        }
                    }
                    if (!copy) dirMoves += d to nd
                }
            }
            for ((d, nd) in dirMoves) {
                val ed = exploreDir
                if (ed != null && (ed == d || ed.startsWith(d + File.separator))) exploreDir = nd + ed.removePrefix(d)
                if (selDirs.remove(d) != null) selDirs[nd] = true
            }
            if (added.isNotEmpty()) items = items + added
            if (moved.isNotEmpty() || added.isNotEmpty() || dirs.isNotEmpty()) applyMoves(moved)
            fsVersion++
            val skipped = blockedFiles.size + blockedDirs.size
            buildString {
                append(if (copy) "$ok copiados a ${dest.name}" else "$ok movidos a ${dest.name}")
                if (ok < files.size + dirs.size) append(" · ${files.size + dirs.size - ok} no se pudieron")
                if (skipped > 0) append(" · $skipped protegidos no se movieron")
            }
        }
    }

    fun delete(t: Targets) {
        val r = root ?: return
        val files = t.files.filterNot { isProtected(it) }
        val dirs = t.dirs.filterNot { dirLocked(it) }
        if (files.isEmpty() && dirs.isEmpty()) {
            say("Están en carpetas protegidas")
            return
        }
        val l = lib ?: return
        work("Enviando ${files.size + dirs.size} a la papelera…") {
            val gone = HashSet<String>()
            val goneDirs = ArrayList<String>()
            val snapshot = items
            var res: Library.TrashResult? = null
            withContext(Dispatchers.IO) {
                if (files.isNotEmpty()) res = l.trash(files).also { gone += it.gone }
                for (d in dirs) {
                    if (toRecycleBin(File(d), r)) {
                        goneDirs += d
                        val pre = d + File.separator
                        snapshot.forEach { if (it.path.startsWith(pre)) gone += it.path }
                    }
                }
            }
            goneDirs.forEach { selDirs.remove(it) }
            val dirsOk = goneDirs.size
            applyRemoval(gone)
            fsVersion++
            val nFiles = res?.gone?.size ?: 0
            buildString {
                append("${nFiles + dirsOk} eliminados")
                append(" · están en la Papelera de reciclaje")
                res?.let { if (it.toFolder > 0) append(" · ${it.toFolder} quedaron en $TRASH_DIR") }
                val skipped = t.count - files.size - dirs.size
                if (skipped > 0) append(" · $skipped protegidos no se tocaron")
            }
        }
    }

    fun rename(path: String, newName: String) {
        val name = newName.trim()
        if (name.isEmpty() || name.any { it in "\\/:*?\"<>|" }) {
            say("Ese nombre no es válido")
            return
        }
        val f = File(path)
        val target = File(f.parentFile, name)
        if (target.exists()) {
            say("Ya existe algo con ese nombre")
            return
        }
        val isDir = f.isDirectory
        if (isDir && dirLocked(path)) {
            say("La carpeta está protegida")
            return
        }
        work("Cambiando nombre…") {
            val ok = withContext(Dispatchers.IO) { runCatching { java.nio.file.Files.move(f.toPath(), target.toPath()) }.isSuccess }
            if (!ok) return@work "No se pudo cambiar el nombre"
            val moved = HashMap<String, Item>()
            if (isDir) {
                val old = path + File.separator
                for (x in items) if (x.path.startsWith(old)) {
                    val n = x.movedTo(target.path + File.separator + x.path.substring(old.length))
                    Media.moveThumb(x, n)
                    moved[x.path] = n
                }
                if (selDirs.remove(path) != null) selDirs[target.path] = true
                val ed = exploreDir
                if (ed != null && (ed == path || ed.startsWith(old))) exploreDir = target.path + ed.removePrefix(path)
            } else {
                items.firstOrNull { it.path == path }?.let { x ->
                    val n = x.movedTo(target.path)
                    Media.moveThumb(x, n)
                    moved[x.path] = n
                    if (sel.remove(path) != null) sel[target.path] = true
                }
            }
            applyMoves(moved)
            fsVersion++
            "Nombre cambiado a $name"
        }
    }

    fun createFolder(inDir: String, name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.any { it in "\\/:*?\"<>|" }) {
            say("Ese nombre no es válido")
            return
        }
        val f = File(inDir, n)
        if (f.exists()) {
            say("Ya existe una carpeta con ese nombre")
            return
        }
        if (f.mkdirs()) {
            fsVersion++
            say("Carpeta «$n» creada")
        } else {
            say("No se pudo crear la carpeta")
        }
    }

    fun trash(all: List<Item>) {
        val l = lib ?: return
        val list = all.filterNot { isProtected(it) }
        if (list.isEmpty()) {
            say("Esos archivos están en carpetas protegidas")
            return
        }
        work("Enviando ${list.size} a la papelera…") {
            val r = withContext(Dispatchers.IO) { l.trash(list) }
            applyRemoval(r.gone)
            buildString {
                append("${r.gone.size} eliminados")
                if (r.toRecycleBin > 0) append(" · están en la Papelera de reciclaje")
                if (r.toFolder > 0) append(" · ${r.toFolder} quedaron en la carpeta $TRASH_DIR")
                val fail = list.size - r.gone.size
                if (fail > 0) append(" · $fail no se pudieron mover")
            }
        }
    }

    fun plan(s: Scheme, looseOnly: Boolean, skipMarked: Boolean): List<Pair<Item, String>> {
        val r = root ?: return emptyList()
        val base = File(r, ORDER_DIR).path + File.separator
        val src = if (s == Scheme.DOC_TYPE || s == Scheme.DOC_TOPIC || s == Scheme.DOC_YEAR) report.docs else report.media
        return src.asSequence()
            .filter { !it.path.startsWith(base) && !isProtected(it) }
            .filter { !looseOnly || it.parent == r.path }
            .filter { !skipMarked || it.path !in report.reasons }
            .map { it to destFor(it, s) }
            .filter { (item, rel) -> File(r, rel).path != item.parent }
            .toList()
    }

    private fun destFor(it: Item, s: Scheme): String {
        val ms = if (it.isMedia) it.taken else it.modified
        val d = localDate(ms)
        val y = d.year
        val m = "%02d".format(d.monthValue)
        return when (s) {
            Scheme.YEAR_MONTH -> "$ORDER_DIR/$y/$y-$m ${MONTH_NAMES[d.monthValue - 1]}"
            Scheme.DAY -> "$ORDER_DIR/$y/$y-$m-${"%02d".format(d.dayOfMonth)}"
            Scheme.SOURCE -> "$ORDER_DIR/" + when {
                it.kind == Kind.VIDEO -> "Videos"
                isScreenshot(it) -> "Capturas de pantalla"
                isWhatsApp(it) -> "WhatsApp"
                it.camera.isNotEmpty() || CAMERA_RX.containsMatchIn(it.name) -> "Cámara"
                else -> "Otras imágenes"
            }
            Scheme.DOC_TYPE -> "$ORDER_DIR/${docType(it.ext).label}"
            Scheme.DOC_TOPIC -> "$ORDER_DIR/${topicOf(it)}"
            Scheme.DOC_YEAR -> "$ORDER_DIR/$y/${docType(it.ext).label}"
        }
    }

    fun organize(plan: List<Pair<Item, String>>) {
        val l = lib ?: return
        work("Ordenando ${plan.size} archivos…") {
            val moved = withContext(Dispatchers.IO) { l.organize(plan) }
            applyMoves(moved)
            canUndo = l.undoFile.exists()
            "${moved.size} archivos ordenados en «$ORDER_DIR». Puedes deshacerlo."
        }
    }

    fun undoOrganize() {
        val l = lib ?: return
        work("Deshaciendo…") {
            val back = withContext(Dispatchers.IO) { l.undo() }
            val moved = HashMap<String, Item>()
            for (it in items) back[it.path]?.let { orig -> moved[it.path] = it.movedTo(orig).also { n -> Media.moveThumb(it, n) } }
            applyMoves(moved)
            canUndo = l.undoFile.exists()
            "${back.size} archivos volvieron a su lugar"
        }
    }

    private suspend fun applyMoves(moved: Map<String, Item>) {
        val r = root ?: return
        val prefix = r.path + File.separator
        val gone = HashSet<String>()
        items = items.mapNotNull { old ->
            val n = moved[old.path]
            when {
                n == null -> old
                n.path.startsWith(prefix) -> n
                else -> { gone += old.path; null }
            }
        }
        moved.keys.forEach { sel.remove(it) }
        viewer?.let { v ->
            v.replace(moved)
            v.drop(gone)
            if (v.list.isEmpty()) viewer = null
        }
        rebuild()
    }

    private suspend fun applyRemoval(gone: Set<String>) {
        items = items.filterNot { it.path in gone }
        gone.forEach { sel.remove(it) }
        viewer?.let { v ->
            v.drop(gone)
            if (v.list.isEmpty()) viewer = null
        }
        rebuild()
    }

    private suspend fun rebuild() {
        val snap = items
        val prot = protectedDirs
        report = withContext(Dispatchers.Default) { Report.build(snap, prot) }
        val l = lib
        if (l != null && !analyzing) scope.launch(Dispatchers.IO) { l.saveIndex(snap) }
    }

    private fun work(label: String, op: suspend () -> String?) {
        scope.launch {
            busy = label
            try {
                op()?.let { say(it) }
            } catch (e: Exception) {
                say("Algo falló: ${e.message ?: e::class.simpleName}")
            } finally {
                busy = null
            }
        }
    }

    fun say(msg: String) {
        messages.tryEmit(msg)
    }

    fun openFile(item: Item) {
        runCatching { Desktop.getDesktop().open(item.file) }.onFailure { say("No se pudo abrir ${item.name}") }
    }

    fun reveal(it: Item) {
        try {
            if (IS_WINDOWS) ProcessBuilder("explorer.exe", "/select,", it.file.absolutePath).start()
            else Desktop.getDesktop().open(it.file.parentFile)
        } catch (_: Exception) {
            say("No se pudo abrir la carpeta")
        }
    }

    fun suggestedFolders(): List<Pair<String, File>> {
        val home = File(System.getProperty("user.home"))
        val od = listOf("OneDriveConsumer", "OneDrive", "OneDriveCommercial").mapNotNull { System.getenv(it) }.map { File(it) }.firstOrNull { it.isDirectory }
        val out = ArrayList<Pair<String, File>>()
        if (od != null) {
            listOf("Imágenes/Álbum de cámara", "Pictures/Camera Roll", "Imágenes", "Pictures").map { File(od, it) }
                .firstOrNull { it.isDirectory }?.let { out += "Fotos de OneDrive" to it }
            out += "OneDrive completo" to od
        }
        listOf("Imágenes" to "Pictures", "Descargas" to "Downloads", "Documentos" to "Documents", "Escritorio" to "Desktop")
            .map { (label, dir) -> label to File(home, dir) }
            .filter { it.second.isDirectory }
            .forEach { out += it }
        return out.distinctBy { it.second.absolutePath.lowercase() }
    }
}

/** Pone archivos en el portapapeles de Windows para pegarlos también en el Explorador. */
class FileListTransfer(private val files: List<File>) : java.awt.datatransfer.Transferable {
    override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
    override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor) = flavor == java.awt.datatransfer.DataFlavor.javaFileListFlavor
    override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any = files
}

fun chooseFolder(title: String, start: File?): File? {
    val fc = JFileChooser(start)
    fc.dialogTitle = title
    fc.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    fc.isAcceptAllFileFilterUsed = false
    return if (fc.showDialog(null, "Elegir") == JFileChooser.APPROVE_OPTION) fc.selectedFile else null
}
