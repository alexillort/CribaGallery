package app.criba.galeria.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

/** Sincroniza el índice local con OneDrive y aplica cambios (mover, eliminar, carpetas). */
class Repo(val auth: AuthProvider, dataDir: File) {
    val graph = Graph(auth)
    private val store = Store(File(dataDir, "indice.bin"))
    private val prefs = Prefs(File(dataDir, "ajustes.properties"))

    fun loadLocal(): Lib {
        store.load()
        return store.build()
    }

    fun lib(): Lib = store.build()

    /** Tamaño de miniatura para la cuadrícula: personalizado si OneDrive lo acepta. */
    val gridThumb: String get() = prefs["thumb"] ?: "medium"

    /**
     * Trae solo lo que cambió desde la última vez (delta query).
     * La primera vez recorre todo OneDrive; después tarda segundos.
     */
    fun sync(onProgress: (count: Int, partial: Boolean) -> Unit) {
        val start = "${Graph.G}/me/drive/root/delta"
        var url = prefs["delta"] ?: start
        var count = 0
        var shownAt = 0
        while (true) {
            val r = graph.exec("GET", url)
            if (r.code == 410) {
                store.clear()
                prefs["delta"] = null
                url = start
                continue
            }
            if (r.code == 401) throw NeedLoginException()
            val json = r.json
            if (r.code !in 200..299 || json == null) throw IOException("OneDrive respondió ${r.code}")

            val arr = json.optJSONArray("value") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                if (id.isEmpty()) continue
                if (o.has("deleted")) {
                    store.remove(id)
                } else {
                    val e = parse(o)
                    if (e != null) store.put(e) else store.remove(id)
                }
            }
            count += arr.length()
            val partial = count - shownAt >= 3000
            if (partial) shownAt = count
            onProgress(count, partial)

            val next = json.optString("@odata.nextLink", "")
            if (next.isNotEmpty()) {
                url = next
                continue
            }
            store.save()
            val delta = json.optString("@odata.deltaLink", "")
            if (delta.isNotEmpty()) prefs["delta"] = delta
            break
        }
        if (prefs["thumb"] == null) probeThumbSize()
    }

    private fun probeThumbSize() {
        val sample = store.build().media.firstOrNull { !it.isVideo } ?: return
        val r = runCatching {
            graph.http.newCall(
                okhttp3.Request.Builder().url(Graph.thumbUrl(sample.id, CUSTOM_THUMB)).get().build()
            ).execute().use { it.code }
        }.getOrDefault(0)
        prefs["thumb"] = if (r == 200) CUSTOM_THUMB else "medium"
    }

    private fun parse(o: JSONObject): Entry? {
        val isRoot = o.has("root")
        val isFolder = o.has("folder") || isRoot
        val file = o.optJSONObject("file")
        val mime = file?.optString("mimeType").orEmpty()
        val isVideo = o.has("video") || mime.startsWith("video/")
        val isImage = o.has("image") || o.has("photo") || mime.startsWith("image/")
        if (!isFolder && !isVideo && !isImage) return null

        val photo = o.optJSONObject("photo")
        val fsi = o.optJSONObject("fileSystemInfo")
        val taken = time(photo?.optString("takenDateTime"))
            ?: time(fsi?.optString("createdDateTime"))
            ?: time(o.optString("createdDateTime"))
            ?: 0L
        val modified = time(fsi?.optString("lastModifiedDateTime"))
            ?: time(o.optString("lastModifiedDateTime"))
            ?: taken
        val dims = o.optJSONObject("image") ?: o.optJSONObject("video")
        val hashes = file?.optJSONObject("hashes")
        val hash = hashes?.optString("quickXorHash")?.takeIf { it.isNotEmpty() }
            ?: hashes?.optString("sha1Hash")?.takeIf { it.isNotEmpty() }
            ?: hashes?.optString("sha256Hash").orEmpty()

        return Entry(
            id = o.getString("id"),
            name = if (isRoot) "OneDrive" else o.optString("name"),
            parentId = o.optJSONObject("parentReference")?.optString("id")?.takeIf { it.isNotEmpty() },
            isFolder = isFolder,
            isRoot = isRoot,
            mime = mime,
            size = o.optLong("size"),
            taken = taken,
            modified = modified,
            width = dims?.optInt("width") ?: 0,
            height = dims?.optInt("height") ?: 0,
            isVideo = !isFolder && isVideo,
            hash = hash,
            webUrl = o.optString("webUrl"),
        )
    }

    private fun time(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
    }

    /** Elimina (a la papelera de OneDrive). Devuelve cuántos se eliminaron. */
    fun delete(ids: List<String>): Int {
        val reqs = ids.mapIndexed { i, id ->
            JSONObject().put("id", "$i").put("method", "DELETE").put("url", "/me/drive/items/$id")
        }
        val res = batch(reqs)
        var ok = 0
        ids.forEachIndexed { i, id ->
            val st = res["$i"] ?: 0
            if (st in 200..299 || st == 404) {
                store.remove(id)
                ok++
            }
        }
        store.save()
        return ok
    }

    /** Mueve a otra carpeta. Devuelve cuántos se movieron. */
    fun move(ids: List<String>, folderId: String): Int {
        val reqs = ids.mapIndexed { i, id ->
            JSONObject()
                .put("id", "$i")
                .put("method", "PATCH")
                .put("url", "/me/drive/items/$id")
                .put("headers", JSONObject().put("Content-Type", "application/json"))
                .put("body", JSONObject().put("parentReference", JSONObject().put("id", folderId)))
        }
        val res = batch(reqs)
        var ok = 0
        ids.forEachIndexed { i, id ->
            if ((res["$i"] ?: 0) in 200..299) {
                store.get(id)?.let { store.put(it.copy(parentId = folderId)) }
                ok++
            }
        }
        store.save()
        return ok
    }

    fun createFolder(parentId: String, name: String): Entry? {
        val body = JSONObject()
            .put("name", name)
            .put("folder", JSONObject())
            .put("@microsoft.graph.conflictBehavior", "rename")
        val r = graph.exec("POST", "${Graph.G}/me/drive/items/$parentId/children", body)
        if (r.code == 401) throw NeedLoginException()
        val e = r.json?.takeIf { r.code in 200..299 }?.let { parse(it) } ?: return null
        store.put(e)
        store.save()
        return e
    }

    /** Hasta 20 operaciones por petición; reintenta las que OneDrive pide esperar. */
    private fun batch(reqs: List<JSONObject>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (chunk in reqs.chunked(20)) {
            var pending = chunk
            var tries = 0
            while (pending.isNotEmpty() && tries < 4) {
                val r = graph.exec("POST", "${Graph.G}/\$batch", JSONObject().put("requests", JSONArray(pending)))
                if (r.code == 401) throw NeedLoginException()
                val json = r.json
                if (r.code !in 200..299 || json == null) throw IOException("OneDrive respondió ${r.code}")
                val resps = json.optJSONArray("responses") ?: JSONArray()
                val retry = ArrayList<JSONObject>()
                var wait = 0L
                for (i in 0 until resps.length()) {
                    val x = resps.getJSONObject(i)
                    val id = x.optString("id")
                    val st = x.optInt("status")
                    if (st == 429 || st == 503) {
                        pending.firstOrNull { it.optString("id") == id }?.let { retry.add(it) }
                        val ra = x.optJSONObject("headers")?.optString("Retry-After")?.toLongOrNull() ?: 3L
                        wait = maxOf(wait, ra)
                    } else {
                        out[id] = st
                    }
                }
                pending = retry
                tries++
                if (pending.isNotEmpty()) Thread.sleep(wait.coerceIn(1, 30) * 1000)
            }
        }
        return out
    }

    fun reset() {
        store.clear()
        store.save()
        prefs.clear()
    }

    companion object {
        const val CUSTOM_THUMB = "c400x400_crop"
    }
}
