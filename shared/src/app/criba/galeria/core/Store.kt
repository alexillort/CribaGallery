package app.criba.galeria.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Índice local de OneDrive. Se guarda en un archivo binario compacto para que
 * la app abra al instante, aunque haya decenas de miles de fotos.
 */
class Store(private val file: File) {
    private val map = HashMap<String, Entry>()

    @Synchronized
    fun load() {
        map.clear()
        if (!file.exists()) return
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { inp ->
                if (inp.readInt() != VERSION) return
                val n = inp.readInt()
                repeat(n) {
                    val e = Entry(
                        id = inp.readUTF(),
                        name = inp.readUTF(),
                        parentId = inp.readUTF().ifEmpty { null },
                        isFolder = inp.readBoolean(),
                        isRoot = inp.readBoolean(),
                        mime = inp.readUTF(),
                        size = inp.readLong(),
                        taken = inp.readLong(),
                        modified = inp.readLong(),
                        width = inp.readInt(),
                        height = inp.readInt(),
                        isVideo = inp.readBoolean(),
                        hash = inp.readUTF(),
                        webUrl = inp.readUTF(),
                    )
                    map[e.id] = e
                }
            }
        } catch (_: Exception) {
            map.clear()
        }
    }

    @Synchronized
    fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { o ->
            o.writeInt(VERSION)
            o.writeInt(map.size)
            for (e in map.values) {
                o.writeUTF(e.id)
                o.writeUTF(e.name)
                o.writeUTF(e.parentId ?: "")
                o.writeBoolean(e.isFolder)
                o.writeBoolean(e.isRoot)
                o.writeUTF(e.mime)
                o.writeLong(e.size)
                o.writeLong(e.taken)
                o.writeLong(e.modified)
                o.writeInt(e.width)
                o.writeInt(e.height)
                o.writeBoolean(e.isVideo)
                o.writeUTF(e.hash)
                o.writeUTF(e.webUrl.take(4000))
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    @Synchronized fun put(e: Entry) { map[e.id] = e }
    @Synchronized fun remove(id: String) { map.remove(id) }
    @Synchronized fun get(id: String): Entry? = map[id]
    @Synchronized fun clear() { map.clear() }
    @Synchronized fun isEmpty(): Boolean = map.isEmpty()

    @Synchronized
    fun build(): Lib {
        val all = map.values.toList()
        val media = all.filter { !it.isFolder }.sortedByDescending { it.taken }
        val folders = all.filter { it.isFolder }.associateBy { it.id }
        return Lib(
            media = media,
            byId = map.toMap(),
            folders = folders,
            mediaByParent = media.groupBy { it.parentId ?: "" },
            foldersByParent = folders.values.groupBy { it.parentId ?: "" }
                .mapValues { (_, v) -> v.sortedBy { it.name.lowercase() } },
            rootId = all.firstOrNull { it.isRoot }?.id,
            totalBytes = media.sumOf { it.size },
        )
    }

    companion object { const val VERSION = 2 }
}

/** Ajustes pequeños (enlace delta, tamaño de miniatura) en un .properties. */
class Prefs(private val file: File) {
    private val p = Properties()

    init {
        if (file.exists()) runCatching { FileInputStream(file).use { p.load(it) } }
    }

    @Synchronized
    operator fun get(key: String): String? = p.getProperty(key)

    @Synchronized
    operator fun set(key: String, value: String?) {
        if (value == null) p.remove(key) else p.setProperty(key, value)
        file.parentFile?.mkdirs()
        runCatching { FileOutputStream(file).use { p.store(it, "Criba") } }
    }

    @Synchronized
    fun clear() {
        p.clear()
        file.delete()
    }
}
