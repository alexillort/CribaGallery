package app.criba.pc

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.W32Errors
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.File

/** Vista previa de la primera página de un PDF, sin depender de programas instalados. */
object PdfPreview {
    fun render(f: File, maxSide: Int): BufferedImage? = try {
        Loader.loadPDF(f).use { doc ->
            if (doc.numberOfPages == 0) return null
            val box = doc.getPage(0).mediaBox
            val scale = maxSide / maxOf(box.width, box.height, 1f)
            PDFRenderer(doc).renderImage(0, scale.coerceIn(0.05f, 4f), ImageType.RGB)
        }
    } catch (_: Throwable) {
        null
    }
}

/**
 * Miniaturas de Windows (las mismas que muestra el Explorador): videos, HEIC,
 * documentos de Office y cualquier formato para el que Windows tenga vista previa.
 */
object ShellThumb {
    private interface Shell32Ex : StdCallLibrary {
        fun SHCreateItemFromParsingName(path: String, pbc: Pointer?, riid: Guid.REFIID, ppv: PointerByReference): WinNT.HRESULT
    }

    private val shell: Shell32Ex? by lazy {
        if (!IS_WINDOWS) null else runCatching { Native.load("shell32", Shell32Ex::class.java, W32APIOptions.UNICODE_OPTIONS) }.getOrNull()
    }

    private val IID_IShellItemImageFactory = Guid.IID("{bcc18b79-ba16-442f-80c4-8a59c30c463b}")

    private val comReady = ThreadLocal.withInitial {
        runCatching { Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED) }
        true
    }

    class SizeV(w: Int, h: Int) : WinUser.SIZE(w, h), Structure.ByValue

    private class ImageFactory(p: Pointer) : Unknown(p) {
        fun getImage(size: SizeV, flags: Int, out: PointerByReference): WinNT.HRESULT =
            _invokeNativeObject(3, arrayOf(pointer, size, flags, out), WinNT.HRESULT::class.java) as WinNT.HRESULT
    }

    private const val SIIGBF_BIGGERSIZEOK = 0x1
    private const val SIIGBF_THUMBNAILONLY = 0x8

    /** null si Windows no tiene miniatura para este archivo (no devuelve íconos genéricos). */
    fun get(path: String, size: Int): BufferedImage? {
        val s = shell ?: return null
        return try {
            comReady.get()
            val ppv = PointerByReference()
            val hr = s.SHCreateItemFromParsingName(path, null, Guid.REFIID(IID_IShellItemImageFactory), ppv)
            if (W32Errors.FAILED(hr) || ppv.value == null) return null
            val factory = ImageFactory(ppv.value)
            try {
                val out = PointerByReference()
                val r = factory.getImage(SizeV(size, size), SIIGBF_BIGGERSIZEOK or SIIGBF_THUMBNAILONLY, out)
                if (W32Errors.FAILED(r) || out.value == null) return null
                val hbm = WinDef.HBITMAP(out.value)
                try {
                    toImage(hbm)
                } finally {
                    GDI32.INSTANCE.DeleteObject(hbm)
                }
            } finally {
                factory.Release()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun toImage(hbm: WinDef.HBITMAP): BufferedImage? {
        val bm = WinGDI.BITMAP()
        GDI32.INSTANCE.GetObject(hbm, bm.size(), bm.pointer)
        bm.read()
        val w = bm.bmWidth.toInt()
        val h = bm.bmHeight.toInt()
        if (w <= 0 || h <= 0) return null
        val info = WinGDI.BITMAPINFO()
        info.bmiHeader.biWidth = w
        info.bmiHeader.biHeight = -h
        info.bmiHeader.biPlanes = 1
        info.bmiHeader.biBitCount = 32
        info.bmiHeader.biCompression = WinGDI.BI_RGB
        val mem = Memory(w.toLong() * h * 4)
        val dc = User32.INSTANCE.GetDC(null)
        try {
            if (GDI32.INSTANCE.GetDIBits(dc, hbm, 0, h, mem, info, WinGDI.DIB_RGB_COLORS) == 0) return null
        } finally {
            User32.INSTANCE.ReleaseDC(null, dc)
        }
        val px = mem.getIntArray(0, w * h)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, w, h, px, 0, w)
        return img
    }
}
