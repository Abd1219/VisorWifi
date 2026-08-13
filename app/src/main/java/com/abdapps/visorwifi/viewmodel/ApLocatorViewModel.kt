package com.abdapps.visorwifi.viewmodel

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdapps.visorwifi.NetworkMonitorService
import com.abdapps.visorwifi.aplocator.SensorOrientationManager
import com.abdapps.visorwifi.engine.aplocator.RssiDirectionAnalyzer
import com.abdapps.visorwifi.model.LatencyPoint
import com.abdapps.visorwifi.model.aplocator.ApInfo
import com.abdapps.visorwifi.model.aplocator.DirectionEstimate
import com.abdapps.visorwifi.model.aplocator.MeasurementPoint
import com.abdapps.visorwifi.model.aplocator.RssiOrientationSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estados de la pantalla AP Locator.
 */
enum class ApLocatorScreenState {
    SELECT_AP,  // Pantalla de información y selección del AP a localizar
    SCANNING,   // Fase de rotación de 360° acumulando muestras de RSSI + orientación
    RESULT      // Fase de visualización del resultado estimado y brújula
}

/**
 * ViewModel que gestiona la lógica de presentación del módulo AP Locator.
 *
 * Administra:
 * - Selección del BSSID objetivo.
 * - Adquisición de muestras en tiempo real (RSSI + azimut de sensores).
 * - Cálculo de progreso de rotación (0°–360°).
 * - Detección de cambio de AP o condiciones problemáticas.
 * - Invocación del motor [RssiDirectionAnalyzer].
 * - Historial en memoria de puntos de medición ([MeasurementPoint]).
 */
class ApLocatorViewModel : ViewModel() {

    private val _screenState = MutableStateFlow(ApLocatorScreenState.SELECT_AP)
    val screenState: StateFlow<ApLocatorScreenState> = _screenState.asStateFlow()

    // AP actualmente seleccionado
    private val _selectedAp = MutableStateFlow<ApInfo?>(null)
    val selectedAp: StateFlow<ApInfo?> = _selectedAp.asStateFlow()

    // Lista de APs visibles (BSSIDs del mismo SSID u otros APs escaneados)
    private val _availableAps = MutableStateFlow<List<ApInfo>>(emptyList())
    val availableAps: StateFlow<List<ApInfo>> = _availableAps.asStateFlow()

    // Azimut actual en tiempo real proveniente de los sensores
    private val _currentAzimuth = MutableStateFlow(0f)
    val currentAzimuth: StateFlow<Float> = _currentAzimuth.asStateFlow()

    // Muestras capturadas en la sesión de escaneo actual
    private val samples = mutableListOf<RssiOrientationSample>()

    // Progreso de cobertura angular (0 a 100%)
    private val _rotationProgress = MutableStateFlow(0)
    val rotationProgress: StateFlow<Int> = _rotationProgress.asStateFlow()

    // Cobertura angular real en grados (0 a 360)
    private val _coveredDegrees = MutableStateFlow(0.0)
    val coveredDegrees: StateFlow<Double> = _coveredDegrees.asStateFlow()

    // Bins de 10° visitados en la sesión actual
    private val visitedBins = mutableSetOf<Int>()

    // Resultado de la estimación de dirección
    private val _directionEstimate = MutableStateFlow<DirectionEstimate?>(null)
    val directionEstimate: StateFlow<DirectionEstimate?> = _directionEstimate.asStateFlow()

    // Historial de puntos de medición registrados en la sesión (en memoria)
    private val _measurementHistory = MutableStateFlow<List<MeasurementPoint>>(emptyList())
    val measurementHistory: StateFlow<List<MeasurementPoint>> = _measurementHistory.asStateFlow()

    // Estado del sensor de orientación
    private val _isSensorAvailable = MutableStateFlow(true)
    val isSensorAvailable: StateFlow<Boolean> = _isSensorAvailable.asStateFlow()

    private val _sensorErrorMessage = MutableStateFlow<String?>(null)
    val sensorErrorMessage: StateFlow<String?> = _sensorErrorMessage.asStateFlow()

    // Referencia al SensorOrientationManager
    private var sensorManager: SensorOrientationManager? = null

    /**
     * Inicializa el gestor de sensores de orientación con el contexto provisto.
     */
    fun initSensors(context: Context) {
        if (sensorManager == null) {
            val manager = SensorOrientationManager(context)
            sensorManager = manager
            _isSensorAvailable.value = manager.isSensorAvailable.value
            _sensorErrorMessage.value = manager.errorMessage.value

            viewModelScope.launch {
                manager.azimuthFlow.collect { az ->
                    _currentAzimuth.value = az
                    if (_screenState.value == ApLocatorScreenState.SCANNING) {
                        onNewAzimuthReceived(az)
                    }
                }
            }
        }
    }

    /**
     * Inicia la captura activa de sensores. Debe ser llamado desde `onResume` o `LaunchedEffect`.
     */
    fun startListeningSensors() {
        sensorManager?.startListening()
    }

    /**
     * Detiene la captura de sensores. Debe ser llamado desde `onPause` o `onDispose`.
     */
    fun stopListeningSensors() {
        sensorManager?.stopListening()
    }

    /**
     * Actualiza la información del AP actualmente conectado a partir de la telemetría.
     */
    fun updateConnectedAp(point: LatencyPoint?, service: NetworkMonitorService?, context: Context) {
        if (point == null) return
        val bssid = point.bssid ?: return
        if (bssid.isBlank() || bssid == "02:00:00:00:00:00") return

        val ssid = point.ssid?.takeIf { it.isNotBlank() } ?: "Wi-Fi Conectado"
        val frequency = point.frequency
        val rssi = point.rssi

        val connectedAp = ApInfo(
            bssid = bssid,
            ssid = ssid,
            rssi = rssi,
            frequencyMHz = frequency,
            isConnected = true
        )

        // Si aún no se ha seleccionado un AP, seleccionar por defecto el AP conectado
        if (_selectedAp.value == null) {
            _selectedAp.value = connectedAp
        } else if (_selectedAp.value?.isConnected == true) {
            // Actualizar lecturas de RSSI del AP conectado en tiempo real
            _selectedAp.value = _selectedAp.value?.copy(
                rssi = rssi,
                frequencyMHz = frequency
            )
        }

        // Refrescar lista de APs disponibles (AP conectado + escaneados si es posible)
        refreshAvailableAps(connectedAp, context)
    }

    /**
     * Intenta obtener APs visibles adicionales mediante `WifiManager.scanResults`.
     */
    private fun refreshAvailableAps(connectedAp: ApInfo, context: Context) {
        val list = mutableListOf(connectedAp)
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val scanResults: List<ScanResult>? = wm?.scanResults
            if (!scanResults.isNullOrEmpty()) {
                scanResults.forEach { result ->
                    if (result.BSSID != null && result.BSSID != connectedAp.bssid) {
                        val ssidName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            result.wifiSsid?.toString()?.removeSurrounding("\"") ?: result.SSID
                        } else {
                            result.SSID
                        }
                        if (!ssidName.isNullOrBlank()) {
                            list.add(
                                ApInfo(
                                    bssid = result.BSSID,
                                    ssid = ssidName,
                                    rssi = result.level,
                                    frequencyMHz = result.frequency,
                                    isConnected = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
            // No hacer nada si falla el escaneo (fallback a solo el AP conectado)
        }
        _availableAps.value = list.distinctBy { it.bssid }
    }

    /**
     * Permite al usuario seleccionar un BSSID específico cuando existen varios APs.
     */
    fun selectAp(apInfo: ApInfo) {
        _selectedAp.value = apInfo
    }

    /**
     * Inicia una nueva sesión de escaneo de 360°.
     */
    fun startScan() {
        val ap = _selectedAp.value ?: return
        samples.clear()
        visitedBins.clear()
        _rotationProgress.value = 0
        _coveredDegrees.value = 0.0
        _directionEstimate.value = null
        _screenState.value = ApLocatorScreenState.SCANNING
    }

    /**
     * Cancela la sesión de escaneo actual y regresa a la selección de AP.
     */
    fun cancelScan() {
        samples.clear()
        visitedBins.clear()
        _rotationProgress.value = 0
        _screenState.value = ApLocatorScreenState.SELECT_AP
    }

    /**
     * Registra una nueva muestra de telemetría RSSI con el azimut actual.
     * Es invocado periódicamente con cada actualización de latencia/RSSI del servicio.
     */
    fun onNewRssiSample(point: LatencyPoint?) {
        if (_screenState.value != ApLocatorScreenState.SCANNING) return
        val targetAp = _selectedAp.value ?: return
        if (point == null) return

        // Detección de cambio de BSSID durante el escaneo
        val currentBssid = point.bssid
        if (currentBssid != null && currentBssid != "02:00:00:00:00:00" && currentBssid != targetAp.bssid) {
            // Invalidate scan
            _directionEstimate.value = DirectionEstimate(
                azimuthDeg = 0.0,
                confidencePercent = 0,
                message = "La conexión cambió de AP. Reinicia la medición.",
                bestRssi = -127,
                avgRssi = -127f,
                warningCode = DirectionEstimate.WarningCode.BSSID_CHANGED
            )
            _screenState.value = ApLocatorScreenState.RESULT
            return
        }

        val rssi = point.rssi
        if (rssi <= -127) return

        val azimuth = _currentAzimuth.value.toDouble()

        val sample = RssiOrientationSample(
            azimuthDeg = azimuth,
            rssi = rssi,
            timestamp = System.currentTimeMillis(),
            bssid = targetAp.bssid,
            frequencyMHz = if (point.frequency > 0) point.frequency else targetAp.frequencyMHz
        )

        samples.add(sample)
        updateRotationCoverage(azimuth)
    }

    /**
     * Registra cambios en el azimut para actualizar la cobertura angular.
     */
    private fun onNewAzimuthReceived(azimuthDeg: Float) {
        if (_screenState.value != ApLocatorScreenState.SCANNING) return
        updateRotationCoverage(azimuthDeg.toDouble())
    }

    /**
     * Actualiza el cálculo del progreso de rotación en base a los sectores de 10° visitados.
     */
    private fun updateRotationCoverage(azimuthDeg: Double) {
        val binIndex = (((azimuthDeg % 360.0) + 360.0) % 360.0 / 10.0).toInt().coerceIn(0, 35)
        visitedBins.add(binIndex)

        val degreesCovered = visitedBins.size * 10.0
        _coveredDegrees.value = degreesCovered

        val progress = ((visitedBins.size / 36.0) * 100.0).toInt().coerceIn(0, 100)
        _rotationProgress.value = progress

        // Si se han alcanzado 360° (36 bins) o suficientes muestras, puede finalizar automáticamente o por botón
    }

    /**
     * Finaliza la fase de escaneo 360° y calcula la dirección estimada usando [RssiDirectionAnalyzer].
     */
    fun finishScan() {
        val targetAp = _selectedAp.value ?: return

        val estimate = RssiDirectionAnalyzer.analyze(
            samples = samples,
            targetBssid = targetAp.bssid
        )

        _directionEstimate.value = estimate
        _screenState.value = ApLocatorScreenState.RESULT

        // Si la estimación fue válida, agregar automáticamente al historial de puntos de la sesión
        if (estimate.isValid && samples.isNotEmpty()) {
            val bestRssi = samples.maxOf { it.rssi }
            val latestRssi = samples.last().rssi
            val newPoint = MeasurementPoint(
                estimatedAzimuthDeg = estimate.azimuthDeg,
                rssi = latestRssi,
                bestRssi = bestRssi,
                bssid = targetAp.bssid,
                frequencyMHz = targetAp.frequencyMHz,
                confidencePercent = estimate.confidencePercent,
                timestamp = System.currentTimeMillis()
            )
            _measurementHistory.value = _measurementHistory.value + newPoint
        }
    }

    /**
     * Regresa a la pantalla de selección de AP para iniciar una nueva medición desde otra posición física.
     */
    fun repeatMeasurement() {
        samples.clear()
        visitedBins.clear()
        _rotationProgress.value = 0
        _directionEstimate.value = null
        _screenState.value = ApLocatorScreenState.SELECT_AP
    }

    /**
     * Elimina todos los puntos de medición guardados en el historial de la sesión actual.
     */
    fun clearHistory() {
        _measurementHistory.value = emptyList()
    }
}
