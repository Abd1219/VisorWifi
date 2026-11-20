package com.abdapps.visorwifi.speedtest.domain

import com.abdapps.visorwifi.speedtest.librespeed.LibreSpeedClient
import com.abdapps.visorwifi.speedtest.librespeed.LibreSpeedServerValidator
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

data class SpeedTestConfig(
    val pingCount: Int = 5,
    val phaseDurationMillis: Long = 10_000,
    val sampleIntervalMillis: Long = 250,
    val timeoutMillis: Int = 15_000,
    /** `garbage.php` chunk count. The official backend emits this many 1 MiB chunks. */
    val downloadChunkSizeMiB: Int = 100,
    /** Known completed-body size. Null means EOF cannot prove a custom endpoint completed normally. */
    val expectedDownloadResponseBytes: Long? = 100L * 1024L * 1024L
)

/**
 * Motor autónomo, de una sola ejecución. No conoce Android ni redes del sistema.
 * Los bytes de subida son bytes aceptados por `OutputStream.write`; una respuesta 2xx confirma
 * la aceptación HTTP de la solicitud completa, pero LibreSpeed no confirma bytes individuales.
 */
class SpeedTestEngine(
    private val client: LibreSpeedClient = LibreSpeedClient(),
    private val validator: LibreSpeedServerValidator = LibreSpeedServerValidator(),
    private val clockNanos: () -> Long = System::nanoTime,
    private val wallClockMillis: () -> Long = System::currentTimeMillis
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    @Volatile private var cancellationReason: String? = null
    private val _status = MutableStateFlow(SpeedTestStatus.IDLE)
    val status: StateFlow<SpeedTestStatus> = _status.asStateFlow()
    private val _samples = MutableSharedFlow<SpeedTestSample>(extraBufferCapacity = 64)
    val samples: SharedFlow<SpeedTestSample> = _samples.asSharedFlow()
    private val _results = MutableSharedFlow<SpeedTestResult>(replay = 1, extraBufferCapacity = 1)
    val results: SharedFlow<SpeedTestResult> = _results.asSharedFlow()

    @Synchronized
    fun start(server: SpeedTestServer, config: SpeedTestConfig = SpeedTestConfig()): Boolean {
        // A cancelled blocking HTTP call may still be unwinding; do not overlap it with a new test.
        if (activeJob?.isCompleted == false) return false
        cancellationReason = null
        activeJob = scope.launch {
            val startedAt = wallClockMillis()
            val id = UUID.randomUUID().toString()
            var testedServer = server
            var probes = LibreSpeedClient.ProbeSummary.empty()
            var download: Double? = null
            var upload: Double? = null
            var terminal = SpeedTestStatus.FAILED
            var error: String? = null
            try {
                changeStatus(SpeedTestStatus.PREPARING)
                testedServer = validator.requireAccessible(server, client, config.timeoutMillis)
                changeStatus(SpeedTestStatus.PINGING)
                probes = client.ping(testedServer, config.pingCount, config.timeoutMillis, clockNanos)
                changeStatus(SpeedTestStatus.DOWNLOADING)
                download = client.download(testedServer, config, clockNanos) { _samples.tryEmit(it) }
                changeStatus(SpeedTestStatus.UPLOADING)
                upload = client.upload(testedServer, config, clockNanos) { _samples.tryEmit(it) }
                changeStatus(SpeedTestStatus.FINALIZING)
                testedServer = testedServer.copy(validationStatus = ServerValidationStatus.COMPATIBLE)
                terminal = SpeedTestStatus.COMPLETED
            } catch (e: CancellationException) {
                terminal = SpeedTestStatus.CANCELLED
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                if (testedServer.validationStatus == ServerValidationStatus.NOT_VALIDATED) {
                    testedServer = testedServer.copy(validationStatus = ServerValidationStatus.VALIDATION_ERROR)
                }
            } finally {
                // Some HttpURLConnection implementations return an I/O error rather than promptly
                // interrupting a blocking read. A cancellation request always wins over that error.
                if (!coroutineContext.isActive || cancellationReason != null) terminal = SpeedTestStatus.CANCELLED
                val result = SpeedTestResult(
                    id, startedAt, wallClockMillis(), testedServer, download, upload,
                    probes.latenciesMs.minOrNull(), probes.latenciesMs.averageOrNull(), probes.latenciesMs.maxOrNull(),
                    SpeedTestMetrics.jitterMillis(probes.latenciesMs), probes.successful, probes.failed,
                    terminal, error, if (terminal == SpeedTestStatus.CANCELLED) cancellationReason else null
                )
                changeStatus(terminal)
                _results.emit(result)
            }
        }
        return true
    }

    @Synchronized
    fun cancel(reason: String = "Cancelled by caller") {
        cancellationReason = reason
        client.cancelActiveRequest()
        activeJob?.cancel()
    }

    fun close() { cancel("Engine closed"); scope.cancel() }

    private fun changeStatus(value: SpeedTestStatus) { _status.value = value }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
