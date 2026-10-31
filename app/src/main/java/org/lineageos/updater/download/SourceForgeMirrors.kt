/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.download

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * SourceForge mirror support: listing the mirrors for a file, pinning a download to one, and
 * picking the fastest.
 *
 * The mirror list comes from SourceForge's mirror picker page (`ul#mirrorList`, one `li` per
 * mirror whose id is the mirror short name), the same page the old crDroid updater parsed. Each
 * mirror is then timed with a small ranged download, and the winner is used through the
 * documented `use_mirror` parameter. If SourceForge doesn't have the file on that mirror, it
 * picks another one itself, so a wrong choice never breaks the download.
 *
 * Every failure (page layout changed, no mirror responds) returns null, which means "Auto".
 */
object SourceForgeMirrors {
    private const val TAG = "SourceForgeMirrors"

    /** Let SourceForge pick the mirror (the link as published). */
    const val MIRROR_AUTO = "auto"

    /** Time a sample from each mirror and use the fastest. */
    const val MIRROR_FASTEST = "fastest"

    /** Show the mirrors for the build when the user taps Download (handled by the UI). */
    const val MIRROR_ASK = "ask"

    data class Mirror(val id: String, val name: String)

    private const val SAMPLE_BYTES = 512 * 1024
    private const val MAX_CANDIDATES = 12
    private const val TEST_DEADLINE_MS = 8_000L
    private const val TIMEOUT_MS = 4_000

    // https://sourceforge.net/projects/<project>/files/<path>/download
    private val DOWNLOAD_URL =
        Regex("""^https://sourceforge\.net/projects/([^/]+)/files/(.+?)/download/?(\?.*)?$""")
    private val MIRROR_LIST =
        Regex("""<ul[^>]*\bid="mirrorList"[^>]*>(.*?)</ul>""", RegexOption.DOT_MATCHES_ALL)
    private val MIRROR_ITEM =
        Regex("""<li[^>]*\bid="([A-Za-z0-9-]+)"[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)

    fun isSourceForgeUrl(url: String) = DOWNLOAD_URL.matches(url)

    /** The download URL pinned to [mirror] through SourceForge's `use_mirror` parameter. */
    fun withMirror(url: String, mirror: String) =
        url.substringBefore('?') + "?use_mirror=" + mirror

    /**
     * The URL to download [url] from for a mirror setting: [MIRROR_AUTO], [MIRROR_FASTEST] or a
     * mirror id. Returns null to keep [url] as is.
     */
    fun resolve(url: String, mirror: String): String? = when {
        !isSourceForgeUrl(url) || mirror == MIRROR_AUTO || mirror == MIRROR_ASK -> null
        mirror == MIRROR_FASTEST -> findFastest(url)
        else -> withMirror(url, mirror)
    }

    /** The mirrors SourceForge offers for a download link; empty if it isn't a SourceForge link. */
    @Throws(IOException::class)
    fun fetchMirrors(url: String): List<Mirror> {
        val match = DOWNLOAD_URL.matchEntire(url) ?: return emptyList()
        val (project, path) = match.destructured
        return fetchMirrors(project, path)
    }

    /** Returns the download URL pinned to the fastest mirror, or null to use Auto. */
    private fun findFastest(url: String): String? {
        val mirrors = try {
            fetchMirrors(url).map { it.id }
        } catch (e: IOException) {
            Log.w(TAG, "Could not fetch mirror list", e)
            return null
        }
        if (mirrors.isEmpty()) {
            Log.w(TAG, "No mirrors found; the mirror page may have changed")
            return null
        }
        val fastest = pickFastest(mirrors.take(MAX_CANDIDATES).associateWith { withMirror(url, it) })
            ?: return null
        Log.d(TAG, "Fastest mirror: $fastest")
        return withMirror(url, fastest)
    }

    private fun fetchMirrors(project: String, path: String): List<Mirror> {
        val pageUrl = "https://sourceforge.net/settings/mirror_choices?projectname=" +
                URLEncoder.encode(project, "UTF-8") + "&filename=" + URLEncoder.encode(path, "UTF-8")
        val conn = URL(pageUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Mirror page replied with ${conn.responseCode}")
            }
            return parseMirrors(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Mirrors from the mirror picker page, without the "autoselect" entry. */
    internal fun parseMirrors(html: String): List<Mirror> {
        val list = MIRROR_LIST.find(html)?.groupValues?.get(1) ?: return emptyList()
        return MIRROR_ITEM.findAll(list)
            .map { Mirror(it.groupValues[1], plainText(it.groupValues[2])) }
            .filter { it.id != "autoselect" }
            .distinctBy { it.id }
            .map { if (it.name.isBlank()) it.copy(name = it.id) else it }
            .toList()
    }

    private fun plainText(html: String) = html
        .replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Downloads the first [SAMPLE_BYTES] from every candidate in parallel and returns the
     * key of the one with the best throughput (latency included), or null if none responded.
     */
    internal fun pickFastest(candidates: Map<String, String>): String? {
        if (candidates.isEmpty()) return null
        val pool = Executors.newFixedThreadPool(candidates.size)
        try {
            val tasks = candidates.map { (name, url) ->
                Callable { name to measure(url) }
            }
            val results = pool.invokeAll(tasks, TEST_DEADLINE_MS, TimeUnit.MILLISECONDS)
                .mapNotNull { f -> runCatching { f.get() }.getOrNull() }
                .filter { it.second > 0 }
            results.sortedByDescending { it.second }.forEach { (name, speed) ->
                Log.d(TAG, "Mirror $name: ${speed / 1024} KB/s")
            }
            return results.maxByOrNull { it.second }?.first
        } finally {
            pool.shutdownNow()
        }
    }

    /** Bytes per second for a sample download, or 0 if the mirror didn't serve the file. */
    private fun measure(url: String): Long {
        val start = System.nanoTime()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Range", "bytes=0-${SAMPLE_BYTES - 1}")
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (conn.responseCode != HttpURLConnection.HTTP_PARTIAL ||
                conn.contentType?.startsWith("text/html", ignoreCase = true) == true
            ) {
                return 0
            }
            var read = 0L
            val buffer = ByteArray(32 * 1024)
            conn.inputStream.use { input ->
                while (read < SAMPLE_BYTES && !Thread.currentThread().isInterrupted) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    read += n
                }
            }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            return if (read < SAMPLE_BYTES / 4 || elapsedMs <= 0) 0 else read * 1000 / elapsedMs
        } catch (e: IOException) {
            return 0
        } finally {
            conn.disconnect()
        }
    }
}
