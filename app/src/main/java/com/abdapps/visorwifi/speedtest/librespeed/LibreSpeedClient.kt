package com.abdapps.visorwifi.speedtest.librespeed

import com.abdapps.visorwifi.speedtest.domain.SpeedTestConfig
import com.abdapps.visorwifi.speedtest.domain.SpeedTestMetrics
import com.abdapps.visorwifi.speedtest.domain.SpeedTestSample
import com.abdapps.visorwifi.speedtest.domain.SpeedTestServer
import com.abdapps.visorwifi.speedtest.domain.SpeedTestStatus
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/** Cliente HTTP streaming para los endpoints declarados de LibreSpeed. */
open class LibreSpeedClient {
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    data class ProbeSummary(val latenciesMs: List<Double>, val successful: Int, val failed: Int) {
        companion object { fun empty() = ProbeSummary(emptyList(), 0, 0) }
    }

    open suspend fun ping(server: SpeedTestServer, count: Int, timeoutMillis: Int, clockNanos: () -> Long): ProbeSummary {
        val successes = mutableListOf<Double>()
        var failures = 0
        repeat(count) {
            coroutineContext.ensureActive()
            try {
                val start = clockNanos()
                execute(server.pingUrl, "GET", timeoutMillis).use { stream -> stream.read() }
                coroutineContext.ensureActive()
                successes += (clockNanos() - start) / 1_000_000.0
            } catch (_: IOException) {
                // disconnect() commonly materializes as SocketException; never turn cancellation into a failed ping.
                coroutineContext.ensureActive()
                failures++
            }
        }
        return ProbeSummary(successes, successes.size, failures)
    }

    open suspend fun download(server: SpeedTestServer, config: SpeedTestConfig, clockNanos: () -> Long, onSample: (SpeedTestSample) -> Unit): Double {
        require(config.phaseDurationMillis > 0) { "phaseDurationMillis must be positive" }
        require(config.downloadChunkSizeMiB in 1..1024) { "downloadChunkSizeMiB must be between 1 and 1024" }
        val targetNanos = config.phaseDurationMillis * 1_000_000
        var activeNanos = 0L
        var lastSample = clockNanos()
        var bytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (activeNanos < targetNanos) {
            coroutineContext.ensureActive()
            var responseBytes = 0L
            val requestStart = clockNanos()
            execute(cacheBustedDownloadUrl(server.downloadUrl, config.downloadChunkSizeMiB), "GET", config.timeoutMillis).use { raw ->
                BufferedInputStream(raw).use { input ->
                    while (activeNanos + (clockNanos() - requestStart) < targetNanos) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        bytes += read
                        responseBytes += read
                        val now = clockNanos()
                        val effective = activeNanos + (now - requestStart)
                        if (now - lastSample >= config.sampleIntervalMillis * 1_000_000) {
                            onSample(SpeedTestSample(now, SpeedTestStatus.DOWNLOADING, bytes, SpeedTestMetrics.megabitsPerSecond(bytes, effective)))
                            lastSample = now
                        }
                    }
                }
            }
            activeNanos += clockNanos() - requestStart
            if (activeNanos >= targetNanos) break
            val expected = config.expectedDownloadResponseBytes
            if (expected == null || responseBytes != expected) {
                onSample(SpeedTestSample(clockNanos(), SpeedTestStatus.DOWNLOADING, bytes, null, "EOF before verified response completion"))
                throw IncompleteDownloadException(bytes, activeNanos)
            }
            // A verified finite LibreSpeed garbage.php body completed; close it, then start one new stream.
            // Inter-request gaps are intentionally excluded from activeNanos and therefore from Mbps.
            yield()
        }
        val elapsed = activeNanos
        if (bytes == 0L) throw IOException("LibreSpeed download returned no body bytes")
        return SpeedTestMetrics.megabitsPerSecond(bytes, elapsed) ?: throw IOException("Invalid download duration")
    }

    open suspend fun upload(server: SpeedTestServer, config: SpeedTestConfig, clockNanos: () -> Long, onSample: (SpeedTestSample) -> Unit): Double {
        val connection = connection(server.uploadUrl, "POST", config.timeoutMillis).apply {
            doOutput = true
            setChunkedStreamingMode(DEFAULT_BUFFER_SIZE)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        activeConnection.set(connection)
        val start = clockNanos(); var lastSample = start; var bytes = 0L
        try {
            connection.outputStream.use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE) { (it and 0xff).toByte() }
                while (clockNanos() - start < config.phaseDurationMillis * 1_000_000) {
                    coroutineContext.ensureActive()
                    output.write(buffer)
                    // write() means the JVM accepted bytes for the socket; not remote per-byte acknowledgement.
                    bytes += buffer.size
                    val now = clockNanos()
                    if (now - lastSample >= config.sampleIntervalMillis * 1_000_000) {
                        onSample(SpeedTestSample(now, SpeedTestStatus.UPLOADING, bytes, SpeedTestMetrics.megabitsPerSecond(bytes, now - start)))
                        lastSample = now
                    }
                }
                output.flush()
            }
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            connection.inputStream.close()
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
        val elapsed = clockNanos() - start
        if (bytes == 0L) throw IOException("No upload bytes were written")
        return SpeedTestMetrics.megabitsPerSecond(bytes, elapsed) ?: throw IOException("Invalid upload duration")
    }

    fun cancelActiveRequest() { activeConnection.getAndSet(null)?.disconnect() }

    protected fun execute(url: String, method: String, timeoutMillis: Int): InputStream = connection(url, method, timeoutMillis).let { connection ->
        activeConnection.set(connection)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            DisconnectingInputStream(connection.inputStream, connection)
        } catch (e: Exception) {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
            throw e
        }
    }

    protected fun connection(url: String, method: String, timeoutMillis: Int): HttpURLConnection =
        (URL(url).openConnection() as? HttpURLConnection ?: throw IOException("URL is not HTTP: $url")).apply {
            requestMethod = method; connectTimeout = timeoutMillis; readTimeout = timeoutMillis
            instanceFollowRedirects = false; useCaches = false; setRequestProperty("Cache-Control", "no-cache")
        }

    private fun cacheBustedDownloadUrl(url: String, chunkSizeMiB: Int): String =
        url + if ('?' in url) "&" else "?" + "r=${System.nanoTime()}&ckSize=$chunkSizeMiB"

    private inner class DisconnectingInputStream(input: InputStream, private val connection: HttpURLConnection) : FilterInputStream(input) {
        override fun close() {
            try { super.close() } finally {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
            }
        }
    }
}

class HttpStatusException(val statusCode: Int) : IOException("HTTP $statusCode")

/** The response ended before the configured measurement window; bytes are diagnostic only. */
class IncompleteDownloadException(val bytesReceived: Long, val elapsedNanos: Long) :
    IOException("Download response ended before measurement window completed")
