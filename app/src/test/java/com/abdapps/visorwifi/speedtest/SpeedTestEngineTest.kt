package com.abdapps.visorwifi.speedtest

import com.abdapps.visorwifi.speedtest.domain.*
import com.abdapps.visorwifi.speedtest.librespeed.LibreSpeedClient
import com.abdapps.visorwifi.speedtest.librespeed.LibreSpeedServerValidator
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestEngineTest {
    @Test fun publishesCompletedResultWithOnlyMeasuredSpeeds() = runBlocking {
        val engine = SpeedTestEngine(FakeClient(), AcceptingValidator())
        assertTrue(engine.start(server))
        val result = engine.results.first()
        assertEquals(SpeedTestStatus.COMPLETED, result.status)
        assertEquals(12.0, result.downloadMbps!!, 0.001)
        assertEquals(6.0, result.uploadMbps!!, 0.001)
        assertEquals(2, result.successfulProbes)
        engine.close()
    }

    @Test fun preventsConcurrentRunsAndMarksCancelledRunTerminal() = runBlocking {
        val engine = SpeedTestEngine(SlowClient(), AcceptingValidator())
        assertTrue(engine.start(server))
        assertFalse(engine.start(server))
        delay(30)
        engine.cancel("test cancellation")
        assertEquals(SpeedTestStatus.CANCELLED, engine.results.first().status)
        engine.close()
    }

    @Test fun clientFailureNeverProducesFictitiousThroughput() = runBlocking {
        val engine = SpeedTestEngine(FailingClient(), AcceptingValidator())
        assertTrue(engine.start(server))
        val result = engine.results.first()
        assertEquals(SpeedTestStatus.FAILED, result.status)
        assertEquals(null, result.downloadMbps)
        assertEquals(null, result.uploadMbps)
        engine.close()
    }

    private open class FakeClient : LibreSpeedClient() {
        override suspend fun ping(server: SpeedTestServer, count: Int, timeoutMillis: Int, clockNanos: () -> Long) =
            ProbeSummary(listOf(10.0, 20.0), 2, 0)
        override suspend fun download(server: SpeedTestServer, config: SpeedTestConfig, clockNanos: () -> Long, onSample: (SpeedTestSample) -> Unit) = 12.0
        override suspend fun upload(server: SpeedTestServer, config: SpeedTestConfig, clockNanos: () -> Long, onSample: (SpeedTestSample) -> Unit) = 6.0
    }
    private class SlowClient : FakeClient() {
        override suspend fun download(server: SpeedTestServer, config: SpeedTestConfig, clockNanos: () -> Long, onSample: (SpeedTestSample) -> Unit): Double {
            delay(5_000); return 1.0
        }
    }
    private class FailingClient : FakeClient() {
        override suspend fun download(server: SpeedTestServer, config: SpeedTestConfig, clockNanos: () -> Long, onSample: (SpeedTestSample) -> Unit): Double =
            throw IOException("HTTP response interrupted")
    }
    private class AcceptingValidator : LibreSpeedServerValidator() {
        override suspend fun requireAccessible(server: SpeedTestServer, client: LibreSpeedClient, timeoutMillis: Int) =
            server.copy(validationStatus = ServerValidationStatus.ACCESSIBLE)
    }
    private companion object {
        val server = SpeedTestServer("local", "Local", "https://test.example", "https://test.example/ping", "https://test.example/dl", "https://test.example/ul", SpeedTestServerType.PRIVATE, SpeedTestProtocol.HTTPS)
    }
}
