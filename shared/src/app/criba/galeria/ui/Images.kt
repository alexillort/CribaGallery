package app.criba.galeria.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Cargador de imágenes: miniaturas pedidas a OneDrive con tu token y guardadas
 * en disco (2 GB). Una vez vista, una miniatura ya no vuelve a descargarse.
 */
fun buildImageLoader(ctx: PlatformContext, app: CribaApp): ImageLoader =
    ImageLoader.Builder(ctx)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { app.repo.graph.http })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(ctx, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(File(app.platform.cacheDir, "miniaturas").toOkioPath())
                .maxSizeBytes(2L * 1024 * 1024 * 1024)
                .build()
        }
        .build()
