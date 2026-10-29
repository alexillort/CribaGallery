package app.criba.galeria.core

/** Un elemento de OneDrive que nos interesa: una carpeta, una foto o un video. */
data class Entry(
    val id: String,
    val name: String,
    val parentId: String?,
    val isFolder: Boolean,
    val isRoot: Boolean,
    val mime: String,
    val size: Long,
    /** Fecha de captura (EXIF) o, si no hay, fecha de creación. Milisegundos. */
    val taken: Long,
    val modified: Long,
    val width: Int,
    val height: Int,
    val isVideo: Boolean,
    val hash: String,
    val webUrl: String,
)

/** Vista calculada del índice local, lista para la interfaz. */
class Lib(
    /** Fotos y videos, más recientes primero. */
    val media: List<Entry>,
    val byId: Map<String, Entry>,
    val folders: Map<String, Entry>,
    val mediaByParent: Map<String, List<Entry>>,
    val foldersByParent: Map<String, List<Entry>>,
    val rootId: String?,
    val totalBytes: Long,
) {
    fun folderName(id: String?): String {
        val f = id?.let { folders[it] } ?: return "OneDrive"
        return if (f.isRoot) "OneDrive" else f.name
    }

    companion object {
        val EMPTY = Lib(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), null, 0)
    }
}

fun fmtBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> String.format("%.1f MB", b / 1048576.0)
    else -> String.format("%.2f GB", b / 1073741824.0)
}
