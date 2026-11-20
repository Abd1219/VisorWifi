package com.abdapps.visorwifi.speedtest

import com.abdapps.visorwifi.speedtest.domain.*
import com.abdapps.visorwifi.speedtest.librespeed.HttpStatusException
import com.abdapps.visorwifi.speedtest.librespeed.IncompleteDownloadException
import com.abdapps.visorwifi.speedtest.librespeed.LibreSpeedClient
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibreSpeedClientTransportTest {
    @Test fun pingCountsRealHttpSuccessAndFailure() = withServer { base -> runBlocking {
        val client = LibreSpeedClient()
        val good = client.ping(server(base, "/ok", "/download", "/upload"), 2, 500, System::nanoTime)
        val bad = client.ping(server(base, "/bad", "/download", "/upload"), 1, 500, System::nanoTime)
        assertEquals(2, good.successful)
        assertEquals(1, bad.failed)
    } }

    @Test fun earlyEofNeverReturnsDownloadMbps() = withServer { base -> runBlocking {
        val error = expectFailure<IncompleteDownloadException> {
            LibreSpeedClient().download(server(base), config(), System::nanoTime) { }
        }
        assertTrue(error.bytesReceived > 0)
    } }

    @Test fun verifiedFiniteResponsesAreRepeatedUntilWindowCompletes() = withSequenceServer { base, requests -> runBlocking {
        val mbps = LibreSpeedClient().download(
            server(base, download = "/sequence"),
            config(phaseMillis = 60, expectedResponseBytes = 4),
            System::nanoTime
        ) { }
        assertTrue(mbps > 0.0)
        assertTrue("Expected more than one finite response", requests.get() > 1)
    } }

    @Test fun errorDuringSuccessiveDownloadFailsWithoutFinalMbps() = withSequenceServer { base, _ -> runBlocking {
        expectFailure<HttpStatusException> {
            LibreSpeedClient().download(
                server(base, download = "/sequenceError"),
                config(phaseMillis = 60, expectedResponseBytes = 4),
                System::nanoTime
            ) { }
        }
    } }

    @Test fun timeoutDuringSuccessiveDownloadFailsWithoutFinalMbps() = withSequenceServer { base, _ -> runBlocking {
        expectFailure<java.io.IOException> {
            LibreSpeedClient().download(
                server(base, download = "/sequenceTimeout"),
                config(timeout = 50, phaseMillis = 60, expectedResponseBytes = 4),
                System::nanoTime
            ) { }
        }
    } }

    @Test fun rejectedUploadHasNoValidThroughput() = withServer { base -> runBlocking {
        val error = expectFailure<java.io.IOException> {
            LibreSpeedClient().upload(server(base, upload = "/reject"), config(), System::nanoTime) { }
        }
        assertTrue(error.message?.contains("413") == true || error.message?.isNotBlank() == true)
    } }

    @Test fun acceptedChunkedUploadProducesMeasuredThroughput() = withServer { base -> runBlocking {
        val mbps = LibreSpeedClient().upload(server(base), config(), System::nanoTime) { }
        assertTrue(mbps > 0.0)
    } }

    @Test fun cancellationDuringValidatorDoesNotComplete() = withServer { base -> runBlocking {
        val engine = SpeedTestEngine()
        assertTrue(engine.start(server(base, ping = "/hang"), config(timeout = 5_000)))
        delay(80)
        engine.cancel("transport cancellation")
        val result = engine.results.first()
        assertEquals(SpeedTestStatus.CANCELLED, result.status)
        engine.close()
    } }

    @Test fun cancellationDuringBlockedDownloadDoesNotComplete() = withServer { base -> runBlocking {
        val engine = SpeedTestEngine()
        assertTrue(engine.start(server(base, download = "/hang"), config(timeout = 5_000)))
        delay(80)
        engine.cancel("download cancellation")
        assertEquals(SpeedTestStatus.CANCELLED, engine.results.first().status)
        engine.close()
    } }

    private fun config(timeout: Int = 500, phaseMillis: Long = 100, expectedResponseBytes: Long? = 100L * 1024L * 1024L) =
        SpeedTestConfig(pingCount = 1, phaseDurationMillis = phaseMillis, sampleIntervalMillis = 20, timeoutMillis = timeout, expectedDownloadResponseBytes = expectedResponseBytes)

    private fun server(base: String, ping: String = "/ok", download: String = "/download", upload: String = "/upload") =
        SpeedTestServer("local", "local", base, "$base$ping", "$base$download", "$base$upload", SpeedTestServerType.PRIVATE, SpeedTestProtocol.HTTP)

    private fun withServer(block: (String) -> Unit) {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/ok") { it.respond(200) }
        http.createContext("/bad") { it.respond(500) }
        http.createContext("/download") { exchange ->
            exchange.sendResponseHeaders(200, 4)
            exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3, 4)) }
        }
        http.createContext("/upload") { exchange ->
            exchange.requestBody.use { input -> while (input.read() != -1) Unit }
            exchange.respond(200)
        }
        http.createContext("/reject") { exchange ->
            exchange.requestBody.close(); exchange.respond(413)
        }
        http.createContext("/hang") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.flush()
            Thread.sleep(500)
            exchange.close()
        }
        http.start()
        try { block("http://127.0.0.1:${http.address.port}") } finally { http.stop(0) }
    }

    private fun withSequenceServer(block: (String, AtomicInteger) -> Unit) {
        val requests = AtomicInteger()
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/sequence") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, 4)
            exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3, 4)) }
        }
        http.createContext("/sequenceError") { exchange ->
            if (requests.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(200, 4)
                exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3, 4)) }
            } else exchange.respond(500)
        }
        http.createContext("/sequenceTimeout") { exchange ->
            if (requests.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(200, 4)
                exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3, 4)) }
            } else {
                Thread.sleep(200)
                exchange.respond(200)
            }
        }
        http.start()
        try { block("http://127.0.0.1:${http.address.port}", requests) } finally { http.stop(0) }
    }

    private fun HttpExchange.respond(code: Int) {
        sendResponseHeaders(code, -1)
        close()
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit): T = try {
        block()
        throw AssertionError("Expected ${T::class.simpleName}")
    } catch (error: Throwable) {
        if (error is T) error else throw error
    }
}
