package com.abdapps.visorwifi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abdapps.visorwifi.speedtest.config.NoApprovedSpeedTestServerSource
import com.abdapps.visorwifi.speedtest.config.SpeedTestServerSource
import com.abdapps.visorwifi.speedtest.connectivity.ConnectivityChangeDetector
import com.abdapps.visorwifi.speedtest.connectivity.ConnectivitySnapshotProvider
import com.abdapps.visorwifi.speedtest.connectivity.NetworkSnapshot
import com.abdapps.visorwifi.speedtest.domain.SpeedTestEngine
import com.abdapps.visorwifi.speedtest.domain.SpeedTestResult
import com.abdapps.visorwifi.speedtest.domain.SpeedTestSample
import com.abdapps.visorwifi.speedtest.domain.SpeedTestStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Adapter seam that lets UI coordination be tested without treating fake readings as real HTTP. */
interface SpeedTestRunner {
    val status: StateFlow<SpeedTestStatus>
    val samples: kotlinx.coroutines.flow.SharedFlow<SpeedTestSample>
    val results: kotlinx.coroutines.flow.SharedFlow<SpeedTestResult>
    fun start(server: com.abdapps.visorwifi.speedtest.domain.SpeedTestServer): Boolean
    fun cancel(reason: String)
    fun close()
}

class EngineSpeedTestRunner(private val engine: SpeedTestEngine = SpeedTestEngine()) : SpeedTestRunner {
    override val status = engine.status
    override val samples = engine.samples
    override val results = engine.results
    override fun start(server: com.abdapps.visorwifi.speedtest.domain.SpeedTestServer) = engine.start(server)
    override fun cancel(reason: String) = engine.cancel(reason)
    override fun close() = engine.close()
}

/** Coordinates existing engine and connectivity contracts; it neither performs HTTP nor infers data. */
class SpeedTestViewModel(
    private val connectivityProvider: ConnectivitySnapshotProvider,
    private val runner: SpeedTestRunner = EngineSpeedTestRunner(),
    private val serverSource: SpeedTestServerSource = NoApprovedSpeedTestServerSource
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SpeedTestUiState(
            currentSnapshot = connectivityProvider.currentSnapshot(),
            isServerConfigured = serverSource.approvedServer() != null
        )
    )
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null
    private var lastObservedSnapshot: NetworkSnapshot = _uiState.value.currentSnapshot

    init {
        viewModelScope.launch { runner.status.collect { status -> _uiState.value = _uiState.value.copy(status = status) } }
        viewModelScope.launch { runner.samples.collect { sample -> _uiState.value = _uiState.value.copy(latestSample = sample) } }
        viewModelScope.launch { runner.results.collect(::onResult) }
    }

    fun onScreenVisible() {
        refreshSnapshot()
        if (observationJob != null) return
        observationJob = viewModelScope.launch {
            connectivityProvider.observe().collect { snapshot ->
                val state = _uiState.value
                val changes = if (state.initialSnapshot == null) emptySet() else
                    ConnectivityChangeDetector.compare(lastObservedSnapshot, snapshot)
                lastObservedSnapshot = snapshot
                _uiState.value = state.copy(
                    currentSnapshot = snapshot,
                    connectivityChanges = state.connectivityChanges + changes
                )
            }
        }
    }

    fun onScreenHidden() {
        observationJob?.cancel()
        observationJob = null
    }

    fun startTest() {
        if (SpeedTestPresentation.isRunning(_uiState.value.status)) return
        val server = serverSource.approvedServer()
        if (server == null) {
            _uiState.value = _uiState.value.copy(
                message = "Servidor no configurado. La prueba real no está disponible",
                isServerConfigured = false
            )
            return
        }
        val initial = connectivityProvider.currentSnapshot()
        lastObservedSnapshot = initial
        _uiState.value = _uiState.value.copy(
            initialSnapshot = initial,
            finalSnapshot = null,
            currentSnapshot = initial,
            connectivityChanges = emptySet(),
            latestSample = null,
            result = null,
            message = null,
            isServerConfigured = true
        )
        if (!runner.start(server)) {
            _uiState.value = _uiState.value.copy(message = "Ya existe una prueba en ejecución.")
        }
    }

    fun cancelTest() = runner.cancel("Cancelled by user")

    fun refreshSnapshot() {
        val snapshot = connectivityProvider.currentSnapshot()
        lastObservedSnapshot = snapshot
        _uiState.value = _uiState.value.copy(currentSnapshot = snapshot)
    }

    private fun onResult(result: SpeedTestResult) {
        val final = connectivityProvider.currentSnapshot()
        val initial = _uiState.value.initialSnapshot
        val changes = if (initial == null) emptySet() else ConnectivityChangeDetector.compare(initial, final)
        _uiState.value = _uiState.value.copy(
            result = result,
            finalSnapshot = final,
            currentSnapshot = final,
            connectivityChanges = _uiState.value.connectivityChanges + changes,
            message = result.error ?: result.cancellationReason
        )
    }

    override fun onCleared() {
        onScreenHidden()
        runner.close()
    }
}

class SpeedTestViewModelFactory(
    private val connectivityProvider: ConnectivitySnapshotProvider,
    private val serverSource: SpeedTestServerSource = NoApprovedSpeedTestServerSource
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SpeedTestViewModel(connectivityProvider, serverSource = serverSource) as T
}
