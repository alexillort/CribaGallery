package app.criba.pc

import java.time.ZoneId

class Moment(val items: List<Item>) {
    val start: Long = items.minOf { it.taken }
    val end: Long = items.maxOf { it.taken }
}

/** Todo lo que la interfaz necesita, calculado de una vez a partir de la lista de archivos. */
class Report(
    val media: List<Item>,
    val docs: List<Item>,
    val moments: List<Moment>,
    val photoDups: List<List<Item>>,
    val similar: List<List<Item>>,
    val blurry: List<Item>,
    val darkOrBright: List<Item>,
    val screenshots: List<Item>,
    val whatsapp: List<Item>,
    val bigVideos: List<Item>,
    val docDups: List<List<Item>>,
    val versions: List<List<Item>>,
    val temp: List<Item>,
    val installers: List<Item>,
    val bigDocs: List<Item>,
    val oldDocs: List<Item>,
    /** Sugeridos para eliminar: ruta → motivo. */
    val reasons: Map<String, String>,
    /** El mejor de cada grupo (se conserva). */
    val best: Set<String>,
    val cloudOnly: Int,
) {
    val mediaBytes: Long = media.sumOf { it.size }
    val docBytes: Long = docs.sumOf { it.size }

    companion object {
        val EMPTY = Report(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            emptyMap(), emptySet(), 0,
        )

        fun build(all: List<Item>): Report {
            val media = all.filter { it.isMedia }.sortedByDescending { it.taken }
            val docs = all.filter { !it.isMedia }.sortedByDescending { it.modified }
            val reasons = HashMap<String, String>()
            val best = HashSet<String>()

            val keepOrder = compareBy<Item>({ COPY_RX.containsMatchIn(it.name) }, { it.name.length }, { it.modified })
            val exact = all.filter { it.sha != null }
                .groupBy { it.sha + "|" + it.size }.values
                .filter { it.size > 1 }
                .map { it.sortedWith(keepOrder) }
                .sortedByDescending { it[0].size * (it.size - 1) }
            for (g in exact) {
                best += g[0].path
                g.drop(1).forEach { reasons.putIfAbsent(it.path, "Duplicado") }
            }

            // Fotos casi iguales: ráfagas tomadas con minutos de diferencia y huella visual parecida.
            val cand = media.filter { it.kind == Kind.PHOTO && it.analyzed && !it.failed && it.dhash != 0L && it.path !in reasons }
                .sortedBy { it.taken }
            val parent = IntArray(cand.size) { it }
            fun find(x: Int): Int {
                var a = x
                while (parent[a] != a) {
                    parent[a] = parent[parent[a]]
                    a = parent[a]
                }
                return a
            }
            for (i in cand.indices) {
                var j = i + 1
                while (j < cand.size && j <= i + 40 && cand[j].taken - cand[i].taken <= 10 * 60_000L) {
                    val d = java.lang.Long.bitCount(cand[i].dhash xor cand[j].dhash)
                    val dt = cand[j].taken - cand[i].taken
                    if (d <= 5 || (d <= 10 && dt <= 120_000L)) parent[find(i)] = find(j)
                    j++
                }
            }
            val similar = cand.indices.groupBy { find(it) }.values
                .filter { it.size > 1 }
                .map { idx -> idx.map { cand[it] }.sortedByDescending { Media.score(it) } }
                .sortedByDescending { g -> g.maxOf { it.taken } }
            for (g in similar) {
                best += g[0].path
                g.drop(1).forEach { reasons.putIfAbsent(it.path, "Casi igual") }
            }

            val sharps = media.filter { it.sharp >= 0 }.map { it.sharp }.sorted()
            val median = if (sharps.isEmpty()) 100.0 else sharps[sharps.size / 2]
            val blurLimit = maxOf(20.0, median * 0.15)
            val blurry = media.filter { it.sharp >= 0 && it.sharp < blurLimit && !isScreenshot(it) }
            blurry.forEach { reasons.putIfAbsent(it.path, "Borrosa") }
            val darkOrBright = media.filter { it.lum >= 0 && (it.lum < 28 || it.lum > 242) && !isScreenshot(it) }
            darkOrBright.forEach { reasons.putIfAbsent(it.path, if (it.lum < 28) "Muy oscura" else "Quemada") }

            val photoDups = exact.filter { it[0].isMedia }
            val docDups = exact.filter { !it[0].isMedia }

            val versions = docs.filter { it.path !in reasons }
                .groupBy { versionBase(it.name) }.values
                .filter { it.size > 1 }
                .map { g -> g.sortedByDescending { it.modified } }
                .sortedByDescending { it[0].modified }
            for (g in versions) {
                best += g[0].path
                g.drop(1).forEach { reasons.putIfAbsent(it.path, "Versión anterior") }
            }
            val temp = docs.filter { TEMP_RX.containsMatchIn(it.name) }
            temp.forEach { reasons.putIfAbsent(it.path, "Temporal") }
            val installers = docs.filter { docType(it.ext) == DOC_TYPES.last() }
            installers.forEach { reasons.putIfAbsent(it.path, "Instalador") }

            val threeYears = System.currentTimeMillis() - 3L * 365 * 86_400_000L

            return Report(
                media = media,
                docs = docs,
                moments = moments(media),
                photoDups = photoDups,
                similar = similar,
                blurry = blurry,
                darkOrBright = darkOrBright,
                screenshots = media.filter { isScreenshot(it) },
                whatsapp = media.filter { isWhatsApp(it) },
                bigVideos = media.filter { it.kind == Kind.VIDEO && it.size > 300L * 1024 * 1024 }.sortedByDescending { it.size },
                docDups = docDups,
                versions = versions,
                temp = temp,
                installers = installers,
                bigDocs = docs.filter { it.size > 200L * 1024 * 1024 }.sortedByDescending { it.size },
                oldDocs = docs.filter { it.modified < threeYears }.sortedBy { it.modified },
                reasons = reasons,
                best = best,
                cloudOnly = all.count { it.cloudOnly },
            )
        }

        /** Momentos: fotos del mismo día separadas por menos de 3 horas. Más recientes primero. */
        private fun moments(media: List<Item>): List<Moment> {
            if (media.isEmpty()) return emptyList()
            val asc = media.sortedBy { it.taken }
            val zone = ZoneId.systemDefault()
            val out = ArrayList<Moment>()
            var cur = ArrayList<Item>()
            var lastDay = -1L
            var last = 0L
            for (it in asc) {
                val day = java.time.Instant.ofEpochMilli(it.taken).atZone(zone).toLocalDate().toEpochDay()
                if (cur.isNotEmpty() && (day != lastDay || it.taken - last > 3 * 3_600_000L)) {
                    out += Moment(cur.asReversed().toList())
                    cur = ArrayList()
                }
                cur += it
                lastDay = day
                last = it.taken
            }
            if (cur.isNotEmpty()) out += Moment(cur.asReversed().toList())
            return out.asReversed()
        }
    }
}
