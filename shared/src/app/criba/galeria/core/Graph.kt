package app.criba.galeria.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cliente mínimo de Microsoft Graph para OneDrive. */
class Graph(private val auth: AuthProvider) {

    /** Cliente compartido con el cargador de imágenes: añade el token solo a graph.microsoft.com. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            if (req.url.host == HOST && req.header("Authorization") == null) {
                chain.proceed(req.newBuilder().header("Authorization", "Bearer " + auth.token()).build())
            } else {
                chain.proceed(req)
            }
        }
        .authenticator { _, resp ->
            if (resp.request.url.host != HOST || resp.priorResponse != null) null
            else resp.request.newBuilder().header("Authorization", "Bearer " + auth.token(true)).build()
        }
        .build()

    class Res(val code: Int, val json: JSONObject?)

    /** Llamada JSON con reintentos cuando OneDrive pide esperar (429/503). */
    fun exec(method: String, url: String, body: JSONObject? = null): Res {
        var attempt = 0
        while (true) {
            val rb = Request.Builder().url(url)
            val payload = (body?.toString() ?: "{}").toRequestBody(JSON)
            when (method) {
                "GET" -> rb.get()
                "DELETE" -> rb.delete()
                else -> rb.method(method, payload)
            }
            val result: Res? = http.newCall(rb.build()).execute().use { resp ->
                val retryable = resp.code == 429 || resp.code == 503 || resp.code == 504
                if (retryable && attempt < 5) {
                    null
                } else {
                    val text = resp.body?.string().orEmpty()
                    val json = if (text.trimStart().startsWith("{")) runCatching { JSONObject(text) }.getOrNull() else null
                    Res(resp.code, json)
                }
            }
            if (result != null) return result
            Thread.sleep((2000L shl attempt).coerceAtMost(30_000))
            attempt++
        }
    }

    companion object {
        const val HOST = "graph.microsoft.com"
        const val G = "https://graph.microsoft.com/v1.0"
        private val JSON = "application/json".toMediaType()

        /** Miniatura generada por OneDrive. size: small, medium, large o c400x400_crop. */
        fun thumbUrl(id: String, size: String) = "$G/me/drive/items/$id/thumbnails/0/$size/content"

        /** Archivo original (OneDrive redirige a la descarga). */
        fun contentUrl(id: String) = "$G/me/drive/items/$id/content"
    }
}
