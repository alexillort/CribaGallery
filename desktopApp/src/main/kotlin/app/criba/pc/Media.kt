package app.criba.pc

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.mov.QuickTimeDirectory
import com.drew.metadata.mp4.Mp4Directory
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import javax.imageio.ImageIO
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Lectura de fechas EXIF, miniaturas en caché y métricas de calidad (nitidez, brillo, huella visual). */
object Media {
    const val THUMB = 360
    lateinit var thumbDir: File

    init {
        ImageIO.setUseCache(false)
    }

    fun thumbFile(it: Item): File = File(thumbDir, sha1("${it.path}|${it.size}|${it.modified}") + ".jpg")

    /** Lee metadatos y genera la miniatura. Seguro de llamar desde varios hilos. */
    fun analyze(item: Item) {
        synchronized(item) {
            if (item.analyzed) return
            try {
                if (!item.cloudOnly) readMeta(item)
                if (item.dateSource == "archivo") {
                    dateFromName(item.name)?.let { d ->
                        item.taken = d
                        item.dateSource = "nombre"
                    }
                }
                if (item.kind == Kind.PHOTO && !item.cloudOnly) makeThumb(item)
            } catch (_: Throwable) {
                item.failed = true
            }
            item.analyzed = true
        }
    }

    private fun readMeta(item: Item) {
        try {
            val md = ImageMetadataReader.readMetadata(item.file)
            val tz = TimeZone.getDefault()
            var date: Date? = null
            md.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)?.let { d ->
                date = d.getDateOriginal(tz) ?: d.getDateDigitized(tz)
            }
            md.getFirstDirectoryOfType(ExifIFD0Directory::class.java)?.let { d ->
                if (date == null) date = d.getDate(ExifIFD0Directory.TAG_DATETIME, tz)
                val make = d.getString(ExifIFD0Directory.TAG_MAKE)?.trim().orEmpty()
                val model = d.getString(ExifIFD0Directory.TAG_MODEL)?.trim().orEmpty()
                item.camera = if (make.isNotEmpty() && model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
                if (d.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) item.orientation = d.getInt(ExifIFD0Directory.TAG_ORIENTATION)
            }
            if (date == null) {
                md.getFirstDirectoryOfType(QuickTimeDirectory::class.java)?.let { d ->
                    date = d.getDate(QuickTimeDirectory.TAG_CREATION_TIME)
                }
            }
            if (date == null) {
                md.getFirstDirectoryOfType(Mp4Directory::class.java)?.let { d ->
                    date = d.getDate(Mp4Directory.TAG_CREATION_TIME)
                }
            }
            val t = date?.time
            if (t != null && t > 347_155_200_000L && t < System.currentTimeMillis() + 2 * 86_400_000L) {
                item.taken = t
                item.dateSource = "EXIF"
            }
        } catch (_: Throwable) {
        }
    }

    private val NAME_DATE = Regex("""(19\d{2}|20\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\d|3[01])(?:[-_ T.]?([01]\d|2[0-3])[-_.:]?([0-5]\d)[-_.:]?([0-5]\d))?""")

    private fun dateFromName(name: String): Long? {
        val m = NAME_DATE.find(name) ?: return null
        val g = m.groupValues
        return try {
            LocalDateTime.of(
                g[1].toInt(), g[2].toInt(), g[3].toInt(),
                g[4].toIntOrNull() ?: 12, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0,
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    /** Decodifica reduciendo al leer (mucho más rápido que abrir la foto completa). */
    fun decode(f: File, target: Int): BufferedImage? {
        try {
            ImageIO.createImageInputStream(f)?.use { iis ->
                val readers = ImageIO.getImageReaders(iis)
                if (readers.hasNext()) {
                    val r = readers.next()
                    try {
                        r.setInput(iis, true, true)
                        val w = r.getWidth(0)
                        val h = r.getHeight(0)
                        val sub = max(1, min(w, h) / target)
                        val p = r.defaultReadParam
                        if (sub > 1) p.setSourceSubsampling(sub, sub, 0, 0)
                        val img = r.read(0, p)
                        if (img != null) return img
                    } finally {
                        r.dispose()
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return try {
            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap().toAwtImage()
        } catch (_: Throwable) {
            null
        }
    }

    /** Escala al lado mayor indicado y endereza según la orientación EXIF. */
    fun fit(src: BufferedImage, maxSide: Int, orientation: Int): BufferedImage {
        val s = min(1.0, maxSide.toDouble() / max(src.width, src.height))
        val w = max(1, (src.width * s).roundToInt())
        val h = max(1, (src.height * s).roundToInt())
        val swap = orientation in 5..8
        val out = BufferedImage(if (swap) h else w, if (swap) w else h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = Color.WHITE
        g.fillRect(0, 0, out.width, out.height)
        val t = AffineTransform()
        val wd = w.toDouble()
        val hd = h.toDouble()
        when (orientation) {
            2 -> { t.translate(wd, 0.0); t.scale(-1.0, 1.0) }
            3 -> { t.translate(wd, hd); t.rotate(Math.PI) }
            4 -> { t.translate(0.0, hd); t.scale(1.0, -1.0) }
            5 -> { t.rotate(Math.PI / 2); t.scale(1.0, -1.0) }
            6 -> { t.translate(hd, 0.0); t.rotate(Math.PI / 2) }
            7 -> { t.translate(hd, wd); t.rotate(-Math.PI / 2); t.scale(1.0, -1.0) }
            8 -> { t.translate(0.0, wd); t.rotate(-Math.PI / 2) }
        }
        g.transform(t)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    fun makeThumb(item: Item) {
        val src = decode(item.file, THUMB)
        if (src == null) {
            item.failed = true
            return
        }
        if (item.width == 0) {
            item.width = src.width
            item.height = src.height
        }
        val t = fit(src, THUMB, item.orientation)
        val out = thumbFile(item)
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        ImageIO.write(t, "jpg", tmp)
        if (!tmp.renameTo(out)) {
            out.delete()
            tmp.renameTo(out)
        }
        metrics(item, t)
    }

    private fun metrics(item: Item, img: BufferedImage) {
        val w = img.width
        val h = img.height
        val px = img.getRGB(0, 0, w, h, null, 0, w)
        val g = FloatArray(w * h)
        var sum = 0.0
        for (i in px.indices) {
            val p = px[i]
            val v = ((p shr 16 and 255) * 0.299 + (p shr 8 and 255) * 0.587 + (p and 255) * 0.114).toFloat()
            g[i] = v
            sum += v
        }
        item.lum = sum / (w * h)
        var m = 0.0
        var m2 = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val l = (g[i - 1] + g[i + 1] + g[i - w] + g[i + w] - 4 * g[i]).toDouble()
                m += l
                m2 += l * l
                n++
            }
        }
        item.sharp = if (n > 0) m2 / n - (m / n) * (m / n) else 0.0

        val cols = 9
        val rows = 8
        val cell = DoubleArray(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = c * w / cols
                val x1 = max(x0 + 1, (c + 1) * w / cols)
                val y0 = r * h / rows
                val y1 = max(y0 + 1, (r + 1) * h / rows)
                var s = 0.0
                var k = 0
                for (y in y0 until min(y1, h)) for (x in x0 until min(x1, w)) { s += g[y * w + x]; k++ }
                cell[r * cols + c] = if (k > 0) s / k else 0.0
            }
        }
        var hash = 0L
        var bit = 0
        for (r in 0 until rows) {
            for (c in 0 until 8) {
                if (cell[r * cols + c] > cell[r * cols + c + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        item.dhash = if (hash == 0L) 1L else hash
    }

    /** Miniatura para la cuadrícula; la genera si aún no existe. */
    fun loadThumb(item: Item): ImageBitmap? {
        if (item.kind != Kind.PHOTO || item.cloudOnly) return null
        val f = thumbFile(item)
        if (!f.exists()) {
            if (!item.analyzed) analyze(item)
            else if (!item.failed) synchronized(item) { runCatching { makeThumb(item) } }
        }
        if (!f.exists()) return null
        return try {
            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    fun loadFull(item: Item, maxSide: Int): ImageBitmap? {
        if (item.kind != Kind.PHOTO || item.cloudOnly) return null
        val src = decode(item.file, maxSide / 2) ?: return null
        return fit(src, maxSide, item.orientation).toComposeImageBitmap()
    }

    /** Al mover un archivo, su miniatura se renombra en vez de generarse otra vez. */
    fun moveThumb(old: Item, new: Item) {
        val a = thumbFile(old)
        if (a.exists()) a.renameTo(thumbFile(new))
    }

    fun score(it: Item): Double =
        ln(max(it.sharp, 0.0) + 1) * 2 + ln((it.width.toDouble() * it.height) + 1) * 0.4 + (if (it.lum in 40.0..225.0) 1.0 else 0.0)
}

fun sha1(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
