package com.lonerxone.siphon.data.engine

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference

class DownloadPausedException : IOException("Download paused")

class HttpResult(
    val contentType: String,
    val contentDisposition: String?,
    val bytes: Long
)

/**
 * Downloads one file over HTTP. Big files are split into 4 to 8 parts that download at the
 * same time. Small files, or servers that refuse ranges, use one connection.
 * A paused download keeps its progress and continues later.
 */
object HttpDownloader {

    private const val MIN_PARALLEL_BYTES = 3L * 1024 * 1024
    private const val DEFAULT_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private class Probe(
        val finalUrl: String,
        val total: Long,
        val rangeOk: Boolean,
        val contentType: String,
        val disposition: String?
    )

    fun download(
        url: String,
        headers: Map<String, String>,
        partFile: File,
        isCancelled: () -> Boolean,
        onProgress: (done: Long, total: Long, speedBps: Long) -> Unit
    ): HttpResult {
        val segsFile = File(partFile.path + ".segs")
        val probe = probe(url, headers)

        // a parallel partial file only makes sense if the server still allows ranges
        if (segsFile.exists() && !(probe.rangeOk && probe.total > 0L)) {
            partFile.delete()
            segsFile.delete()
        }

        val hasSequentialPartial = !segsFile.exists() && partFile.exists() && partFile.length() > 0L

        if (!hasSequentialPartial && probe.rangeOk && probe.total >= MIN_PARALLEL_BYTES) {
            try {
                downloadParallel(probe, headers, partFile, segsFile, isCancelled, onProgress)
                return HttpResult(probe.contentType, probe.disposition, probe.total)
            } catch (e: DownloadPausedException) {
                throw e
            } catch (e: Exception) {
                // the server did not like many connections: start again with a single one
                partFile.delete()
                segsFile.delete()
            }
        }

        val bytes = downloadSingle(probe.finalUrl, headers, partFile, isCancelled, onProgress)
        return HttpResult(probe.contentType, probe.disposition, bytes)
    }

    private fun open(url: String, headers: Map<String, String>, range: String?): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", DEFAULT_AGENT)
            setRequestProperty("Accept", "*/*")
            headers.forEach { (name, value) ->
                if (value.isNotBlank() &&
                    !name.equals("Accept-Encoding", ignoreCase = true) &&
                    !name.equals("Range", ignoreCase = true)
                ) {
                    setRequestProperty(name, value)
                }
            }
            setRequestProperty("Accept-Encoding", "identity")
            if (range != null) setRequestProperty("Range", range)
        }
        return conn
    }

    private fun probe(startUrl: String, headers: Map<String, String>): Probe {
        var current = startUrl
        repeat(6) {
            val conn = open(current, headers, "bytes=0-0")
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirect without a location")
                current = URL(URL(current), location).toString()
            } else if (code == 206) {
                val range = conn.getHeaderField("Content-Range") ?: ""
                val type = (conn.contentType ?: "").substringBefore(";").trim().lowercase()
                val disposition = conn.getHeaderField("Content-Disposition")
                conn.disconnect()
                val total = range.substringAfterLast('/').toLongOrNull() ?: -1L
                return Probe(current, total, total > 0L, type, disposition)
            } else if (code in 200..299) {
                val length = conn.contentLengthLong
                val type = (conn.contentType ?: "").substringBefore(";").trim().lowercase()
                val disposition = conn.getHeaderField("Content-Disposition")
                conn.disconnect()
                return Probe(current, length, false, type, disposition)
            } else {
                conn.disconnect()
                throw IOException("Server returned HTTP $code")
            }
        }
        throw IOException("Too many redirects")
    }

    private fun downloadSingle(
        url: String,
        headers: Map<String, String>,
        partFile: File,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long, Long) -> Unit
    ): Long {
        var startAt = if (partFile.exists()) partFile.length() else 0L

        var conn = open(url, headers, if (startAt > 0L) "bytes=$startAt-" else null)
        var code = conn.responseCode
        if (code == 416) {
            conn.disconnect()
            partFile.delete()
            startAt = 0L
            conn = open(url, headers, null)
            code = conn.responseCode
        }
        if (code != 200 && code != 206) {
            conn.disconnect()
            throw IOException("Server returned HTTP $code")
        }

        val resumed = code == 206 && startAt > 0L
        if (!resumed) startAt = 0L

        val remaining = conn.contentLengthLong
        val total = if (remaining > 0L) remaining + startAt else 0L
        var done = startAt
        var lastTick = System.currentTimeMillis()
        var lastBytes = done

        try {
            conn.inputStream.use { input ->
                FileOutputStream(partFile, resumed).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (isCancelled()) {
                            output.flush()
                            throw DownloadPausedException()
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read

                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 500L) {
                            val speed = ((done - lastBytes) * 1000L) / (now - lastTick)
                            onProgress(done, total, speed)
                            lastTick = now
                            lastBytes = done
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            conn.disconnect()
        }

        if (total > 0L && done < total) throw IOException("Connection closed early")
        return done
    }

    private fun downloadParallel(
        probe: Probe,
        headers: Map<String, String>,
        partFile: File,
        segsFile: File,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long, Long) -> Unit
    ) {
        val total = probe.total
        val parts = if (total > 40L * 1024 * 1024) 8 else 4
        val chunk = total / parts
        val starts = LongArray(parts) { it * chunk }
        val ends = LongArray(parts) { if (it == parts - 1) total - 1 else it * chunk + chunk - 1 }
        val done = AtomicLongArray(parts)

        // continue an earlier parallel download
        if (segsFile.exists() && partFile.exists() && partFile.length() == total) {
            val lines = segsFile.readLines()
            if (lines.size == parts + 1 && lines[0] == total.toString()) {
                for (i in 0 until parts) {
                    val length = ends[i] - starts[i] + 1
                    done.set(i, (lines[i + 1].toLongOrNull() ?: 0L).coerceIn(0L, length))
                }
            } else {
                partFile.delete()
                segsFile.delete()
            }
        }
        if (!partFile.exists()) {
            RandomAccessFile(partFile, "rw").use { it.setLength(total) }
        }

        fun saveSegs() {
            try {
                val text = total.toString() + "\n" + (0 until parts).joinToString("\n") { done.get(it).toString() }
                segsFile.writeText(text)
            } catch (e: Exception) {
            }
        }
        saveSegs()

        val stop = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val pool = Executors.newFixedThreadPool(parts)

        try {
            val futures = (0 until parts).map { i ->
                pool.submit(Callable<Unit> {
                    try {
                        var stalls = 0
                        while (true) {
                            val length = ends[i] - starts[i] + 1
                            if (done.get(i) >= length) break
                            if (stop.get()) throw DownloadPausedException()

                            val before = done.get(i)
                            try {
                                val from = starts[i] + before
                                val conn = open(probe.finalUrl, headers, "bytes=$from-${ends[i]}")
                                try {
                                    if (conn.responseCode != 206) {
                                        throw IOException("The server refused a partial request")
                                    }
                                    RandomAccessFile(partFile, "rw").use { raf ->
                                        raf.seek(from)
                                        conn.inputStream.use { input ->
                                            val buffer = ByteArray(64 * 1024)
                                            while (true) {
                                                if (stop.get()) throw DownloadPausedException()
                                                val read = input.read(buffer)
                                                if (read == -1) break
                                                raf.write(buffer, 0, read)
                                                done.addAndGet(i, read.toLong())
                                            }
                                        }
                                    }
                                } finally {
                                    conn.disconnect()
                                }
                            } catch (e: DownloadPausedException) {
                                throw e
                            } catch (e: IOException) {
                                stalls++
                                if (stalls > 3) throw e
                                Thread.sleep(400L * stalls)
                            }

                            if (done.get(i) == before) {
                                stalls++
                                if (stalls > 3) throw IOException("The server stopped sending data")
                            } else {
                                stalls = 0
                            }
                        }
                    } catch (t: Throwable) {
                        if (t !is DownloadPausedException) failure.set(t)
                        stop.set(true)
                        throw t
                    }
                })
            }

            var lastTick = System.currentTimeMillis()
            var lastBytes = (0 until parts).sumOf { done.get(it) }
            var lastSave = lastTick

            while (futures.any { !it.isDone } && failure.get() == null) {
                Thread.sleep(300)
                if (isCancelled()) stop.set(true)

                val now = System.currentTimeMillis()
                val soFar = (0 until parts).sumOf { done.get(it) }
                val speed = ((soFar - lastBytes) * 1000L) / maxOf(1L, now - lastTick)
                onProgress(soFar, total, speed)
                lastTick = now
                lastBytes = soFar

                if (now - lastSave > 1500L) {
                    saveSegs()
                    lastSave = now
                }
            }

            stop.set(true)
            saveSegs()

            futures.forEach { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    // handled below through the shared failure value
                }
            }

            val problem = failure.get()
            if (problem != null) throw problem
            if (isCancelled()) throw DownloadPausedException()

            val finished = (0 until parts).sumOf { done.get(it) }
            if (finished < total) throw IOException("The download did not finish")
            segsFile.delete()
        } finally {
            pool.shutdownNow()
        }
    }
}