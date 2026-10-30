package app.criba.pc

import java.io.File
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Kind { PHOTO, VIDEO, DOC }

/** Un archivo de la carpeta. Los campos mutables se llenan al analizarlo y se guardan en el índice. */
class Item(
    val path: String,
    val name: String,
    val ext: String,
    val kind: Kind,
    val size: Long,
    val modified: Long,
    /** Archivo de OneDrive que solo está en la nube: no se lee para no descargarlo. */
    val cloudOnly: Boolean,
) : Serializable {
    var taken: Long = modified
    var dateSource: String = "archivo"
    var camera: String = ""
    var width: Int = 0
    var height: Int = 0
    var orientation: Int = 1
    var dhash: Long = 0L
    var sharp: Double = -1.0
    var lum: Double = -1.0
    var analyzed: Boolean = false
    var failed: Boolean = false
    var sha: String? = null

    val isMedia: Boolean get() = kind != Kind.DOC
    val file: File get() = File(path)
    val parent: String get() = File(path).parent ?: ""
    val folderName: String get() = File(path).parentFile?.name ?: ""

    fun movedTo(newPath: String): Item = Item(newPath, File(newPath).name, ext, kind, size, modified, cloudOnly).also {
        it.taken = taken
        it.dateSource = dateSource
        it.camera = camera
        it.width = width
        it.height = height
        it.orientation = orientation
        it.dhash = dhash
        it.sharp = sharp
        it.lum = lum
        it.analyzed = analyzed
        it.failed = failed
        it.sha = sha
    }

    companion object {
        private const val serialVersionUID = 4L
    }
}

val PHOTO_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "tif", "tiff", "avif", "jfif")
val VIDEO_EXT = setOf("mp4", "mov", "m4v", "avi", "mkv", "3gp", "webm", "wmv", "mts")

class DocType(val label: String, val color: Long, val exts: Set<String>)

val DOC_TYPES = listOf(
    DocType("PDF", 0xFFC5462C, setOf("pdf")),
    DocType("Texto y Word", 0xFF2F62B5, setOf("doc", "docx", "odt", "rtf", "txt", "md", "pages", "tex")),
    DocType("Hojas de cálculo", 0xFF2D7A58, setOf("xls", "xlsx", "xlsm", "ods", "csv", "tsv", "numbers")),
    DocType("Presentaciones", 0xFFC2741B, setOf("ppt", "pptx", "odp", "key")),
    DocType("Diseño y 3D", 0xFF8A4FB0, setOf("psd", "ai", "svg", "fig", "xd", "blend", "fbx", "obj", "glb", "gltf", "uasset", "max", "ma", "mb")),
    DocType("Audio", 0xFF4B7F92, setOf("mp3", "wav", "m4a", "ogg", "flac", "aac", "wma")),
    DocType("Comprimidos", 0xFF6A6A3A, setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2")),
    DocType("Código y datos", 0xFF3E5566, setOf("py", "ipynb", "js", "ts", "json", "xml", "html", "css", "c", "cpp", "h", "cs", "java", "kt", "m", "r", "sql", "yaml", "yml", "sh", "bat", "ps1", "unity", "mat", "h5", "pt", "pth", "onnx", "npy", "npz")),
    DocType("Instaladores", 0xFF8C3B3B, setOf("exe", "msi", "dmg", "pkg", "apk", "iso", "appx", "msix")),
)
val OTHER_TYPE = DocType("Otros", 0xFF59636D, emptySet())

private val TYPE_BY_EXT: Map<String, DocType> = DOC_TYPES.flatMap { t -> t.exts.map { it to t } }.toMap()
fun docType(ext: String): DocType = TYPE_BY_EXT[ext] ?: OTHER_TYPE

private val TOPICS = listOf(
    "Facturas y recibos" to Regex("""factura|recibo|invoice|receipt|cuenta.?de.?cobro|comprobante|ticket|pago|cotizaci""", RegexOption.IGNORE_CASE),
    "Bancos e impuestos" to Regex("""extracto|estado.?de.?cuenta|banco|bank|statement|impuesto|declaraci|renta|\bsat\b|dian|n[oó]mina|payroll|\brut\b""", RegexOption.IGNORE_CASE),
    "Contratos y legal" to Regex("""contrato|contract|acuerdo|agreement|poder\b|legal|escritura|otros[ií]|\bops\b|\bcps\b""", RegexOption.IGNORE_CASE),
    "Identidad y trámites" to Regex("""c[eé]dula|pasaporte|passport|\bine\b|curp|\brfc\b|licencia|visa|acta|tr[aá]mite|identific""", RegexOption.IGNORE_CASE),
    "Certificados" to Regex("""certificad|certificate|diploma|constancia""", RegexOption.IGNORE_CASE),
    "Trabajo y CV" to Regex("""\bcv\b|curr[ií]cul|resume|hoja.?de.?vida|cover|oferta|propuesta|carta""", RegexOption.IGNORE_CASE),
    "Académico" to Regex("""tesis|thesis|paper|art[ií]culo|article|tarea|clase|curso|diplomado|syllabus|examen|apuntes|seminario|maestr|doctorad|review|revisi|congreso|conference""", RegexOption.IGNORE_CASE),
    "Salud" to Regex("""receta|m[eé]dic|cl[ií]nic|laboratorio|hospital|\beps\b|imss|incapacidad|historia.?cl""", RegexOption.IGNORE_CASE),
    "Viajes" to Regex("""reserva|booking|vuelo|flight|boarding|hotel|itinerar|tiquete|pasabordo""", RegexOption.IGNORE_CASE),
    "Escaneos" to Regex("""scan|escane|camscanner""", RegexOption.IGNORE_CASE),
)
const val NO_TOPIC = "Sin tema claro"

fun topicOf(it: Item): String {
    val text = it.name + " " + it.folderName
    return TOPICS.firstOrNull { (_, rx) -> rx.containsMatchIn(text) }?.first ?: NO_TOPIC
}

val SHOT_RX = Regex("""screenshot|captura|pantallazo|screen[ _-]?shot|^scr_""", RegexOption.IGNORE_CASE)
val WA_RX = Regex("""-WA\d{3,}|whatsapp""", RegexOption.IGNORE_CASE)
val COPY_RX = Regex("""\(\d+\)|[-_ ](copia|copy)(\b|$)""", RegexOption.IGNORE_CASE)
val TEMP_RX = Regex("""^~\$|\.tmp$|\.crdownload$|\.part$|^\.~lock|^thumbs\.db$|^desktop\.ini$|\.ds_store$""", RegexOption.IGNORE_CASE)
val CAMERA_RX = Regex("""^(img|dsc|dscn|pxl|mvimg|photo|p\d{6,})[_-]""", RegexOption.IGNORE_CASE)
private val VERSION_RX = Regex("""[\s_\-.]*(\(\d+\)|copia|copy|final\d*|definitiv[oa]|def|ok|corregid[oa]|v\d+|ver\d+|version\s?\d+|rev\d*)$""", RegexOption.IGNORE_CASE)

fun isScreenshot(it: Item) = SHOT_RX.containsMatchIn(it.name) || SHOT_RX.containsMatchIn(it.folderName)
fun isWhatsApp(it: Item) = WA_RX.containsMatchIn(it.name) || WA_RX.containsMatchIn(it.folderName)

/** "Tesis_cap3_v2.docx" y "Tesis_cap3_final.docx" comparten base: son versiones del mismo documento. */
fun versionBase(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    var b = name.substringBeforeLast('.', name).lowercase().trim()
    while (true) {
        val n = b.replace(VERSION_RX, "").trim()
        if (n == b || n.isEmpty()) break
        b = n
    }
    return "$b|$ext"
}

fun fmtBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
}

private val ES: Locale = Locale.forLanguageTag("es")
private val DAY_FMT = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM yyyy", ES)
private val MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", ES)
private val SHORT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", ES)
private val FULL_FMT = DateTimeFormatter.ofPattern("d 'de' MMMM yyyy · HH:mm", ES)
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", ES)
val MONTH_NAMES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
private fun cap(s: String) = s.replaceFirstChar { it.titlecase(ES) }

fun dayLabel(ms: Long): String {
    val d = localDate(ms)
    val today = LocalDate.now()
    return when (d) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> cap(DAY_FMT.format(d))
    }
}

fun monthLabel(ms: Long): String = cap(MONTH_FMT.format(localDate(ms)))
fun shortDate(ms: Long): String = SHORT_FMT.format(localDate(ms))
fun fullDate(ms: Long): String = FULL_FMT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun timeOf(ms: Long): String = TIME_FMT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
