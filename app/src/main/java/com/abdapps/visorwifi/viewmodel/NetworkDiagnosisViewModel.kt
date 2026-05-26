package com.abdapps.visorwifi.viewmodel

import androidx.lifecycle.ViewModel
import com.abdapps.visorwifi.engine.NetworkDiagnosisEngine
import com.abdapps.visorwifi.model.DiagnosisState
import com.abdapps.visorwifi.model.DiagnosisStatus
import com.abdapps.visorwifi.model.LatencyPoint
import com.abdapps.visorwifi.model.NetworkMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * ViewModel que gestiona el estado del bloque "Diagnóstico de Red".
 *
 * Se encarga de procesar los puntos telemétricos históricos provenientes del servicio
 * y convertirlos en métricas de red estructuradas [NetworkMetrics], alimentando al
 * motor de diagnóstico [NetworkDiagnosisEngine].
 */
class NetworkDiagnosisViewModel : ViewModel() {

    private val _diagnosisState = MutableStateFlow(
        DiagnosisState(
            status = DiagnosisStatus.LOADING,
            title = "Recolectando información para diagnóstico…",
            confidence = 100,
            evidences = listOf("Recolectando muestras iniciales…"),
            recommendation = "Espera a que el servicio obtenga suficientes lecturas de red."
        )
    )
    val diagnosisState: StateFlow<DiagnosisState> = _diagnosisState.asStateFlow()

    /**
     * Punto de entrada para actualizar el diagnóstico a partir de las métricas de telemetría actuales.
     *
     * @param history Lista de puntos de medición histórica capturados por el servicio.
     * @param rssi    RSSI actual en dBm (-127 si no disponible).
     */
    fun updateFromMetrics(history: List<LatencyPoint>, rssi: Int) {
        val minSamples = 10
        if (history.size < minSamples) {
            _diagnosisState.value = DiagnosisState(
                status = DiagnosisStatus.LOADING,
                title = "Recolectando información para diagnóstico…",
                confidence = 100,
                evidences = listOf("Muestras actuales: ${history.size}/$minSamples"),
                recommendation = "Espera a que el servicio obtenga suficientes lecturas de red."
            )
            return
        }

        val metrics = calculateMetrics(history, rssi)
        _diagnosisState.value = NetworkDiagnosisEngine.evaluateDiagnosis(metrics)
    }

    /**
     * Consolida el historial de telemetría de red en métricas estructuradas.
     */
    private fun calculateMetrics(history: List<LatencyPoint>, currentRssi: Int): NetworkMetrics {
        // Filtrar mediciones válidas (descartar pings fallidos representados con -1f)
        val lanPoints = history.map { it.lanLatency }.filter { it >= 0 }
        val wanPoints = history.map { it.wanLatency }.filter { it >= 0 }

        val lanLatencyAvgMs = if (lanPoints.isNotEmpty()) lanPoints.average().toFloat() else 0f
        val lanLatencyMaxMs = if (lanPoints.isNotEmpty()) lanPoints.maxOrNull() ?: 0f else 0f

        val wanLatencyAvgMs = if (wanPoints.isNotEmpty()) wanPoints.average().toFloat() else 0f
        val wanLatencyMaxMs = if (wanPoints.isNotEmpty()) wanPoints.maxOrNull() ?: 0f else 0f

        // Jitter: promedio de diferencias absolutas de latencia WAN entre muestras consecutivas válidas
        var jitterSum = 0f
        var jitterPairsCount = 0
        for (i in 1 until history.size) {
            val p1 = history[i - 1].wanLatency
            val p2 = history[i].wanLatency
            if (p1 >= 0 && p2 >= 0) {
                jitterSum += abs(p2 - p1)
                jitterPairsCount++
            }
        }
        val jitterMs = if (jitterPairsCount > 0) jitterSum / jitterPairsCount else 0f

        // RSSI: usar el valor del servicio, o fallback al del último punto del historial
        val rssiDbm = if (currentRssi > -127) currentRssi else (history.lastOrNull()?.rssi ?: -127)

        // PHY Rate
        val latest = history.lastOrNull()
        val phyRateMbps = when {
            latest == null -> 0
            latest.txLinkSpeed > 0 -> latest.txLinkSpeed
            latest.linkSpeed > 0 -> latest.linkSpeed
            else -> 0
        }

        // Pérdida de paquetes WAN (timeouts / intentos totales)
        val totalPoints = history.size
        val wanTimeouts = history.count { it.wanLatency < 0 }
        val packetLossPercent = if (totalPoints > 0) (wanTimeouts.toFloat() / totalPoints) * 100f else 0f

        return NetworkMetrics(
            lanLatencyAvgMs = lanLatencyAvgMs,
            lanLatencyMaxMs = lanLatencyMaxMs,
            wanLatencyAvgMs = wanLatencyAvgMs,
            wanLatencyMaxMs = wanLatencyMaxMs,
            jitterMs = jitterMs,
            rssiDbm = rssiDbm,
            phyRateMbps = phyRateMbps,
            packetLossPercent = packetLossPercent
        )
    }
}
