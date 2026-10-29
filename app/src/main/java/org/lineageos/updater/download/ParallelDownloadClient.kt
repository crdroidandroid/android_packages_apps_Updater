/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.download

import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * The file is split into [connections] byte ranges, each fetched over its own HTTP connection
 * into a separate part file next to the destination. When every range is complete, the parts
 * are appended into the first one (deleting each as it is consumed, so peak extra disk usage is
 * one part) and that file is renamed onto the destination.
 *
 * Resume needs no extra state: each part file's length is its progress, and the part file names
 * carry the total size and connection count, so stale parts from a different build or layout
 * are never reused.
 *
 * Falls back to [HttpURLConnectionClient] (single connection) when the server doesn't honour
 * range requests, the file is small, or a download was started by the single-connection client.
 */
class ParallelDownloadClient internal constructor(
    private val url: String,
    private val destination: File,
    private val progressListener: DownloadClient.ProgressListener?,
    private val callback: DownloadClient.DownloadCallback,
    private val useDuplicateLinks: Boolean,
    connections: Int,
) : DownloadClient {

    private val connections = connections.coerceIn(1, MAX_CONNECTIONS)
    private val lock = Any()
    private var session: Session? = null
    private var fallback: DownloadClient? = null

    override fun start() = launch(resume = false)

    override fun resume() = launch(resume = true)

    override fun cancel() {
        synchronized(lock) {
            fallback?.let {
                it.cancel()
                fallback = null
                return
            }
            val s = session ?: run {
                Log.e(TAG, "Not downloading")
                return
            }
            session = null
            s.cancel()
        }
    }

    private fun launch(resume: Boolean) {
        synchronized(lock) {
            if (session != null || fallback != null) {
                Log.e(TAG, "Already downloading")
                return
            }
            if (connections < 2) {
                startFallbackLocked(resume)
                return
            }
            if (resume) {
                if (!destination.exists()) {
                    callback.onFailure(false)
                    return
                }
                if (existingPartFiles(destination).isEmpty() && destination.length() > 0) {
                    // Started by the single-connection client; finish it the same way.
                    startFallbackLocked(resume = true)
                    return
                }
            }
            session = Session(resume).also { it.start() }
        }
    }

    private fun startFallbackLocked(resume: Boolean) {
        try {
            val client = HttpURLConnectionClient(
                url, destination, progressListener, callback, useDuplicateLinks,
            )
            fallback = client
            if (resume) client.resume() else client.start()
        } catch (e: IOException) {
            Log.e(TAG, "Could not create fallback client", e)
            callback.onFailure(false)
        }
    }

    /** Called from a session thread when parallel download isn't possible. */
    private fun switchToFallback(from: Session) {
        synchronized(lock) {
            if (session !== from) {
                // Cancelled while probing.
                callback.onFailure(true)
                return
            }
            session = null
            deletePartFiles(destination)
            destination.delete()
            startFallbackLocked(resume = false)
        }
    }

    private class Segment(val index: Int, val start: Long, val end: Long, val file: File) {
        val length get() = end - start + 1
        val isComplete get() = file.length() >= length
    }

    private class Probe(val url: String, val totalLength: Long, val headers: Map<String, String>)

    private inner class Session(private val resume: Boolean) : Thread("ParallelDownload") {
        @Volatile
        private var cancelled = false
        private val connectionsInUse = ConcurrentHashMap.newKeySet<HttpURLConnection>()
        private val segmentThreads = mutableListOf<Thread>()
        private val failure = AtomicReference<Throwable?>(null)
        private val downloaded = AtomicLong(0)
        private val shouldStop get() = cancelled || failure.get() != null

        // Speed / ETA state, only touched by this thread.
        private var lastMillis = 0L
        private var lastSampleBytes = 0L
        private var speed = -1L
        private var eta = -1L
        private var lastEta = -1.0

        fun cancel() {
            cancelled = true
            connectionsInUse.forEach { it.disconnect() }
            interrupt()
        }

        override fun run() {
            try {
                if (!resume) {
                    if (cancelled) {
                        callback.onFailure(true)
                        return
                    }
                    deletePartFiles(destination)
                    // Empty marker file: the controller requires the destination to exist
                    // before it will try to resume.
                    FileOutputStream(destination, false).close()
                }

                val probe = probe()
                if (cancelled) {
                    callback.onFailure(true)
                    return
                }
                if (probe == null) {
                    switchToFallback(this)
                    return
                }

                val total = probe.totalLength
                callback.onResponse(DownloadClient.Headers { name ->
                    if (name.equals("Content-Length", ignoreCase = true)) total.toString()
                    else probe.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
                })

                val segments = prepareSegments(total)
                downloaded.set(segments.sumOf { minOf(it.file.length(), it.length) })

                val pending = segments.filterNot { it.isComplete }
                val latch = CountDownLatch(pending.size)
                pending.forEach { seg ->
                    val t = Thread({
                        try {
                            downloadSegment(probe.url, seg)
                        } finally {
                            latch.countDown()
                        }
                    }, "ParallelDownload-${seg.index}")
                    segmentThreads += t
                    t.start()
                }

                lastMillis = SystemClock.elapsedRealtime()
                lastSampleBytes = downloaded.get()
                while (!latch.await(REPORT_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                    if (cancelled || failure.get() != null) break
                    reportProgress(total)
                }
                stopSegments()

                if (cancelled) {
                    callback.onFailure(true)
                    return
                }
                failure.get()?.let {
                    Log.e(TAG, "Parallel download failed", it)
                    callback.onFailure(false)
                    return
                }

                reportProgress(total)
                mergeParts(segments, total)
                speed = 0
                eta = 0
                progressListener?.update(total, total, speed, eta)
                callback.onSuccess()
            } catch (e: InterruptedException) {
                stopSegments()
                callback.onFailure(true)
            } catch (e: IOException) {
                Log.e(TAG, "Error downloading file", e)
                stopSegments()
                callback.onFailure(cancelled)
            }
        }

        /**
         * Resolves redirects (SourceForge -> mirror) and checks range support with a 1-byte
         * request. Returns null when a parallel download isn't possible or worthwhile.
         */
        private fun probe(): Probe? {
            val conn = openConnection(url, "bytes=0-0", followRedirects = true)
            try {
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Server ignored range request; using single connection")
                    return null
                }
                if (code != HttpURLConnection.HTTP_PARTIAL) {
                    throw IOException("Server replied with $code")
                }
                if (conn.contentType?.startsWith("text/html", ignoreCase = true) == true) {
                    Log.d(TAG, "Got HTML instead of a file; using single connection")
                    return null
                }
                val total = conn.getHeaderField("Content-Range")
                    ?.substringAfterLast('/')?.trim()?.toLongOrNull()
                if (total == null || total < MIN_PARALLEL_SIZE) {
                    Log.d(TAG, "Unknown or small size ($total); using single connection")
                    return null
                }
                val headers = conn.headerFields.entries
                    .filter { it.key != null && !it.value.isNullOrEmpty() }
                    .associate { it.key to it.value.last() }
                Log.d(TAG, "Downloading $total bytes from ${conn.url} with $connections connections")
                return Probe(conn.url.toString(), total, headers)
            } finally {
                conn.disconnect()
                connectionsInUse.remove(conn)
            }
        }

        private fun prepareSegments(total: Long): List<Segment> {
            val size = total / connections
            val segments = (0 until connections).map { i ->
                val start = i * size
                val end = if (i == connections - 1) total - 1 else start + size - 1
                Segment(i, start, end, partFile(destination, total, connections, i))
            }
            val expected = segments.map { it.file.name }.toSet()
            existingPartFiles(destination)
                .filter { it.name !in expected }
                .forEach { it.delete() }
            segments.forEach { seg ->
                if (!resume) {
                    seg.file.delete()
                } else if (seg.file.length() > seg.length) {
                    // Interrupted merge appended later parts; the prefix is still valid.
                    RandomAccessFile(seg.file, "rw").use { it.setLength(seg.length) }
                }
            }
            return segments
        }

        private fun downloadSegment(url: String, seg: Segment) {
            var failures = 0
            while (!shouldStop && !seg.isComplete) {
                val before = seg.file.length()
                try {
                    fetchRange(url, seg)
                } catch (e: IOException) {
                    if (shouldStop) return
                    if (seg.file.length() > before) failures = 0
                    if (++failures > MAX_RETRIES) {
                        failure.compareAndSet(null, e)
                        return
                    }
                    Log.w(TAG, "Segment ${seg.index} failed (attempt $failures), retrying", e)
                    try {
                        sleep(RETRY_DELAY_MS * failures)
                    } catch (ie: InterruptedException) {
                        return
                    }
                }
            }
        }

        private fun fetchRange(url: String, seg: Segment) {
            val from = seg.start + seg.file.length()
            val conn = openConnection(url, "bytes=$from-${seg.end}", followRedirects = true)
            try {
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_PARTIAL) {
                    throw IOException("Expected HTTP 206 for segment ${seg.index}, got $code")
                }
                val contentRange = conn.getHeaderField("Content-Range")
                if (contentRange == null || !contentRange.startsWith("bytes $from-${seg.end}/")) {
                    throw IOException("Unexpected Content-Range: $contentRange")
                }
                conn.inputStream.use { input ->
                    FileOutputStream(seg.file, true).use { out ->
                        val buffer = ByteArray(CHUNK_SIZE)
                        var remaining = seg.end - from + 1
                        while (remaining > 0 && !shouldStop) {
                            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            remaining -= n
                            downloaded.addAndGet(n.toLong())
                        }
                    }
                }
                if (!shouldStop && !seg.isComplete) {
                    throw IOException("Segment ${seg.index} ended early")
                }
            } finally {
                conn.disconnect()
                connectionsInUse.remove(conn)
            }
        }

        private fun openConnection(
            url: String,
            range: String,
            followRedirects: Boolean,
        ): HttpURLConnection {
            val conn = URL(url).openConnection() as HttpURLConnection
            connectionsInUse += conn
            if (cancelled) {
                conn.disconnect()
                throw IOException("Cancelled")
            }
            conn.instanceFollowRedirects = followRedirects
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Range", range)
            // Avoid transparent gzip: byte ranges must map 1:1 onto the file.
            conn.setRequestProperty("Accept-Encoding", "identity")
            return conn
        }

        /** Stops and waits for segment threads so no writes outlive the callback. */
        private fun stopSegments() {
            if (shouldStop) {
                connectionsInUse.forEach { it.disconnect() }
                segmentThreads.forEach { it.interrupt() }
            }
            segmentThreads.forEach { t ->
                while (t.isAlive) {
                    try {
                        t.join()
                    } catch (_: InterruptedException) {
                        // Keep waiting; the segment threads are already being torn down.
                    }
                }
            }
        }

        private fun mergeParts(segments: List<Segment>, total: Long) {
            val first = segments.first().file
            FileOutputStream(first, true).channel.use { out ->
                for (seg in segments.drop(1)) {
                    FileInputStream(seg.file).channel.use { input ->
                        val size = input.size()
                        var pos = 0L
                        while (pos < size) {
                            pos += input.transferTo(pos, size - pos, out)
                        }
                    }
                    out.force(false)
                    seg.file.delete()
                }
            }
            if (first.length() != total) {
                throw IOException("Merged size ${first.length()} != expected $total")
            }
            destination.delete()
            if (!first.renameTo(destination)) {
                throw IOException("Could not rename ${first.name} to ${destination.name}")
            }
        }

        // Same smoothing as HttpURLConnectionClient so the UI behaves identically.
        private fun reportProgress(total: Long) {
            val now = SystemClock.elapsedRealtime()
            val bytes = downloaded.get()
            val delta = now - lastMillis
            if (delta > 0) {
                val cur = ((bytes - lastSampleBytes) * 1000) / delta
                speed = if (speed == -1L) cur else ((speed * 3) + cur) / 4
                lastMillis = now
                lastSampleBytes = bytes
            }
            if (speed > 0) {
                var raw = (total - bytes).toDouble() / speed
                if (lastEta >= 0 && raw > lastEta / 2) {
                    var diff = raw - lastEta
                    raw = lastEta + (if (diff < 0) 0.3 else 0.1) * diff
                    diff = raw - lastEta
                    val diffPct = (diff / lastEta) * 100
                    if (Math.abs(diff) < 5 || Math.abs(diffPct) < 5) {
                        raw = lastEta - (if (diff < 0) 0.4 else 0.2)
                    }
                }
                lastEta = maxOf(raw, 1.0)
                eta = lastEta.toLong()
            }
            progressListener?.update(bytes, total, speed, eta)
        }
    }

    companion object {
        private const val TAG = "ParallelDownloadClient"

        const val DEFAULT_CONNECTIONS = 8
        private const val MAX_CONNECTIONS = 16

        /** Below this size the extra connections aren't worth it. */
        private const val MIN_PARALLEL_SIZE = 16L * 1024 * 1024

        private const val CHUNK_SIZE = 32 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val REPORT_INTERVAL_MS = 500L
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 1_000L

        private const val PART_SUFFIX = ".part"

        private fun partFile(destination: File, total: Long, count: Int, index: Int) =
            File(destination.parentFile, "${destination.name}.$total.$count.$index$PART_SUFFIX")

        private fun existingPartFiles(destination: File): List<File> =
            destination.parentFile?.listFiles { f ->
                f.name.startsWith("${destination.name}.") && f.name.endsWith(PART_SUFFIX)
            }?.toList().orEmpty()

        /** Removes leftover part files for [destination], e.g. when a paused download is deleted. */
        @JvmStatic
        fun deletePartFiles(destination: File) {
            existingPartFiles(destination).forEach { it.delete() }
        }

        /** Bytes downloaded so far, whether they're in [destination] or still in part files. */
        @JvmStatic
        fun getDownloadedBytes(destination: File): Long =
            destination.length() + existingPartFiles(destination).sumOf { it.length() }
    }
}
