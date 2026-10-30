package app.criba.pc

import java.awt.Desktop
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.RandomAccessFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

const val ORDER_DIR = "Ordenado"
const val TRASH_DIR = "_Criba_papelera"

private val SKIP_DIRS = setOf(
    "\$recycle.bin", "system volume information", "appdata", "node_modules", ".git", ".svn",
    "_criba_papelera", "windows", "program files", "program files (x86)", "programdata",
    ".venv", "venv", "__pycache__", ".gradle", ".idea", "library", "temp",
)

val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("win")

/** Una carpeta abierta en Criba: escaneo, índice en disco y operaciones sobre archivos. */
class Library(rootDir: File, dataDir: File) {
    val root: File = rootDir.absoluteFile
    private val key = sha1(root.path.lowercase())
    private val indexFile = File(dataDir, "indices/$key.bin")
    val undoFile = File(dataDir, "deshacer/$key.txt")

    @Suppress("UNCHECKED_CAST")
    fun loadIndex(): Map<String, Item> = try {
        ObjectInputStream(BufferedInputStream(FileInputStream(indexFile))).use { (it.readObject() as ArrayList<Item>) }
            .associateBy { it.path }
    } catch (_: Throwable) {
        emptyMap()
    }

    fun saveIndex(items: List<Item>) {
        try {
            indexFile.parentFile?.mkdirs()
            val tmp = File(indexFile.path + ".tmp")
            ObjectOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { it.writeObject(ArrayList(items)) }
            if (!tmp.renameTo(indexFile)) {
                indexFile.delete()
                tmp.renameTo(indexFile)
            }
        } catch (_: Throwable) {
        }
    }

    /** Recorre la carpeta. Reutiliza el análisis previo de los archivos que no cambiaron. */
    fun scan(prev: Map<String, Item>, counter: AtomicInteger): List<Item> {
        val out = ArrayList<Item>()
        val rootPath = root.toPath()
        Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != rootPath) {
                    val n = dir.fileName?.toString()?.lowercase() ?: return FileVisitResult.CONTINUE
                    if (n in SKIP_DIRS || n.startsWith(".")) return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
                val name = file.fileName.toString()
                val ext = name.substringAfterLast('.', "").lowercase()
                val kind = when (ext) {
                    in PHOTO_EXT -> Kind.PHOTO
                    in VIDEO_EXT -> Kind.VIDEO
                    else -> Kind.DOC
                }
                val size = attrs.size()
                val mod = attrs.lastModifiedTime().toMillis()
                val p = file.toString()
                val cloud = isCloudOnly(file)
                val old = prev[p]
                out += if (old != null && old.size == size && old.modified == mod && old.cloudOnly == cloud) old
                else Item(p, name, ext, kind, size, mod, cloud)
                counter.incrementAndGet()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return out
    }

    /** OneDrive "Archivos a petición": los que están solo en la nube tienen estas marcas. */
    private fun isCloudOnly(p: Path): Boolean {
        if (!IS_WINDOWS) return false
        return try {
            val a = Files.getAttribute(p, "dos:attributes", LinkOption.NOFOLLOW_LINKS) as Int
            (a and (0x400000 or 0x40000 or 0x1000)) != 0
        } catch (_: Throwable) {
            false
        }
    }

    /** Huella de contenido solo para archivos con el mismo tamaño que otro (posibles duplicados). */
    fun hashDuplicates(items: List<Item>) {
        items.filter { !it.cloudOnly && it.size > 0 }
            .groupBy { it.size }.values.filter { it.size > 1 }.flatten()
            .filter { it.sha == null }
            .parallelStream()
            .forEach { it.sha = runCatching { contentHash(it.file, it.size) }.getOrNull() }
    }

    private fun contentHash(f: File, size: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 16)
        RandomAccessFile(f, "r").use { raf ->
            if (size <= 64L * 1024 * 1024) {
                while (true) {
                    val n = raf.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            } else {
                // Archivos grandes: principio, mitad y final bastan para distinguirlos.
                for (start in listOf(0L, size / 2, size - 4L * 1024 * 1024)) {
                    raf.seek(start)
                    var left = 4L * 1024 * 1024
                    while (left > 0) {
                        val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n <= 0) break
                        md.update(buf, 0, n)
                        left -= n
                    }
                }
                md.update(size.toString().toByteArray())
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun move(list: List<Item>, dest: File): Map<String, Item> {
        val moved = LinkedHashMap<String, Item>()
        dest.mkdirs()
        for (it in list) {
            try {
                if (it.file.parentFile?.absoluteFile == dest.absoluteFile) continue
                val target = uniqueTarget(dest, it.name)
                Files.move(it.file.toPath(), target.toPath())
                val n = it.movedTo(target.path)
                Media.moveThumb(it, n)
                moved[it.path] = n
            } catch (_: Exception) {
            }
        }
        return moved
    }

    class TrashResult(val gone: Set<String>, val toRecycleBin: Int, val toFolder: Int)

    fun trash(list: List<Item>): TrashResult {
        val gone = HashSet<String>()
        var bin = 0
        var folder = 0
        val desk = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        val canTrash = desk?.isSupported(Desktop.Action.MOVE_TO_TRASH) == true
        for (it in list) {
            val f = it.file
            if (!f.exists()) {
                gone += it.path
                continue
            }
            val ok = try {
                canTrash && desk!!.moveToTrash(f)
            } catch (_: Exception) {
                false
            }
            if (ok) {
                gone += it.path
                bin++
            } else {
                try {
                    val dir = File(root, TRASH_DIR)
                    dir.mkdirs()
                    Files.move(f.toPath(), uniqueTarget(dir, f.name).toPath())
                    gone += it.path
                    folder++
                } catch (_: Exception) {
                }
            }
        }
        return TrashResult(gone, bin, folder)
    }

    /** Mueve según el plan y guarda un registro para poder deshacer. */
    fun organize(plan: List<Pair<Item, String>>): Map<String, Item> {
        val moved = LinkedHashMap<String, Item>()
        val log = StringBuilder()
        for ((item, rel) in plan) {
            try {
                val dir = File(root, rel)
                dir.mkdirs()
                val target = uniqueTarget(dir, item.name)
                Files.move(item.file.toPath(), target.toPath())
                val n = item.movedTo(target.path)
                Media.moveThumb(item, n)
                moved[item.path] = n
                log.append(target.path).append('\t').append(item.path).append('\n')
            } catch (_: Exception) {
            }
        }
        undoFile.parentFile?.mkdirs()
        undoFile.writeText(log.toString())
        return moved
    }

    /** Devuelve cada archivo a su lugar. Resultado: ruta actual → ruta original. */
    fun undo(): Map<String, String> {
        val back = LinkedHashMap<String, String>()
        if (!undoFile.exists()) return back
        undoFile.readLines().asReversed().forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 2) {
                val now = File(parts[0])
                val orig = File(parts[1])
                if (now.exists() && !orig.exists()) {
                    try {
                        orig.parentFile?.mkdirs()
                        Files.move(now.toPath(), orig.toPath())
                        back[now.path] = orig.path
                    } catch (_: Exception) {
                    }
                }
            }
        }
        removeEmptyDirs(File(root, ORDER_DIR))
        undoFile.delete()
        return back
    }

    private fun removeEmptyDirs(dir: File) {
        if (!dir.isDirectory) return
        dir.listFiles()?.filter { it.isDirectory }?.forEach { removeEmptyDirs(it) }
        if (dir.listFiles()?.isEmpty() == true) dir.delete()
    }
}

fun uniqueTarget(dir: File, name: String): File {
    var f = File(dir, name)
    if (!f.exists()) return f
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var k = 1
    while (true) {
        f = File(dir, "$base ($k)$ext")
        if (!f.exists()) return f
        k++
    }
}
