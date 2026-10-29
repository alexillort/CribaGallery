package app.criba.galeria.core

/** Grupos de fotos y videos que conviene revisar para liberar espacio. */
class Cleanup(
    val dupGroups: List<List<Entry>>,
    /** Copias sobrantes de cada grupo (se conserva la primera). */
    val dupExtras: List<Entry>,
    val screenshots: List<Entry>,
    val bigVideos: List<Entry>,
    val whatsapp: List<Entry>,
)

private val COPY = Regex("""\(\d+\)|[-_ ](copia|copy)\b""", RegexOption.IGNORE_CASE)
private val SHOT = Regex("""screenshot|captura|screen[_ ]?shot|pantallazo""", RegexOption.IGNORE_CASE)
private val WA = Regex("""-WA\d{3,}|whatsapp""", RegexOption.IGNORE_CASE)

fun analyze(lib: Lib): Cleanup {
    val groups = lib.media
        .filter { it.hash.isNotEmpty() }
        .groupBy { it.hash + "|" + it.size }
        .values
        .filter { it.size > 1 }
        .map { g -> g.sortedWith(compareBy<Entry>({ COPY.containsMatchIn(it.name) }, { it.name.length }, { it.modified })) }
        .sortedByDescending { it.first().size * (it.size - 1) }

    fun folderOf(e: Entry) = lib.folders[e.parentId ?: ""]?.name.orEmpty()

    return Cleanup(
        dupGroups = groups,
        dupExtras = groups.flatMap { it.drop(1) },
        screenshots = lib.media.filter { SHOT.containsMatchIn(it.name) || SHOT.containsMatchIn(folderOf(it)) },
        bigVideos = lib.media.filter { it.isVideo && it.size > 150L * 1024 * 1024 }.sortedByDescending { it.size },
        whatsapp = lib.media.filter { WA.containsMatchIn(it.name) || WA.containsMatchIn(folderOf(it)) },
    )
}
