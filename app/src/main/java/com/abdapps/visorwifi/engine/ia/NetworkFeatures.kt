package com.abdapps.visorwifi.engine.ia

import com.abdapps.visorwifi.model.LatencyPoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Almacena las 13 características estadísticas procesadas a partir de una ventana de tiempo.
 * Estas variables alimentan la inferencia del modelo de IA local de VisorWifi.
 */
data class NetworkFeatures(
    val lanLatencyAvgMs: Float,
    val lanLatencyStdMs: Float,
    val lanLatencyMaxMs: Float,
    val wanLatencyAvgMs: Float,
    val wanLatencyStdMs: Float,
    val wanLatencyMaxMs: Float,
    val jitterAvgMs: Float,
    val jitterMaxMs: Float,
    val rssiAvgDbm: Float,
    val rssiMinDbm: Float,
    val phyRateAvgMbps: Float,
    val packetLossPercent: Float,
    val diffLanWanAvgMs: Float
) {
    companion object {
        /**
         * Extrae y consolida las características avanzadas a partir de una lista histórica de mediciones.
         *
         * @param history Lista con los puntos de medición de la ventana (ej: últimos 120-300 puntos).
         * @return Instancia consolidada de [NetworkFeatures].
         */
        fun fromHistory(history: List<LatencyPoint>): NetworkFeatures {
            if (history.isEmpty()) {
                return NetworkFeatures(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -127f, -127f, 0f, 0f, 0f)
            }

            // 1. Filtrar mediciones válidas (descartar timeouts / fallos representados con < 0)
            val lanPoints = history.map { it.lanLatency }.filter { it >= 0f }
            val wanPoints = history.map { it.wanLatency }.filter { it >= 0f }

            // Promedios y máximos
            val lanLatencyAvgMs = if (lanPoints.isNotEmpty()) lanPoints.average().toFloat() else 0f
            val lanLatencyMaxMs = if (lanPoints.isNotEmpty()) lanPoints.maxOrNull() ?: 0f else 0f

            val wanLatencyAvgMs = if (wanPoints.isNotEmpty()) wanPoints.average().toFloat() else 0f
            val wanLatencyMaxMs = if (wanPoints.isNotEmpty()) wanPoints.maxOrNull() ?: 0f else 0f

            // Desviaciones estándar
            val lanLatencyStdMs = calculateStdDev(lanPoints, lanLatencyAvgMs)
            val wanLatencyStdMs = calculateStdDev(wanPoints, wanLatencyAvgMs)

            // 2. Jitter: Variabilidad consecutiva en la latencia WAN
            val jitterValues = mutableListOf<Float>()
            for (i in 1 until history.size) {
                val p1 = history[i - 1].wanLatency
                val p2 = history[i].wanLatency
                if (p1 >= 0f && p2 >= 0f) {
                    jitterValues.add(abs(p2 - p1))
                }
            }
            val jitterAvgMs = if (jitterValues.isNotEmpty()) jitterValues.average().toFloat() else 0f
            val jitterMaxMs = if (jitterValues.isNotEmpty()) jitterValues.maxOrNull() ?: 0f else 0f

            // 3. RSSI: Filtrar valores por defecto (-127 suele indicar sin señal o error de lectura)
            val rssiPoints = history.map { it.rssi.toFloat() }.filter { it > -127f }
            val rssiAvgDbm = if (rssiPoints.isNotEmpty()) rssiPoints.average().toFloat() else -127f
            val rssiMinDbm = if (rssiPoints.isNotEmpty()) rssiPoints.minOrNull() ?: -127f else -127f

            // 4. Velocidad física de enlace (PHY Link Rate) promedio
            val phyPoints = history.map {
                when {
                    it.txLinkSpeed > 0 -> it.txLinkSpeed.toFloat()
                    it.linkSpeed > 0 -> it.linkSpeed.toFloat()
                    else -> 0f
                }
            }.filter { it > 0f }
            val phyRateAvgMbps = if (phyPoints.isNotEmpty()) phyPoints.average().toFloat() else 0f

            // 5. Pérdida de paquetes WAN (timeouts / total puntos)
            val totalPoints = history.size
            val wanTimeouts = history.count { it.wanLatency < 0f }
            val packetLossPercent = if (totalPoints > 0) (wanTimeouts.toFloat() / totalPoints) * 100f else 0f

            // 6. Diferencia de latencias WAN y LAN
            val diffLanWanAvgMs = if (wanPoints.isNotEmpty() && lanPoints.isNotEmpty()) {
                maxOf(0f, wanLatencyAvgMs - lanLatencyAvgMs)
            } else {
                0f
            }

            return NetworkFeatures(
                lanLatencyAvgMs = lanLatencyAvgMs,
                lanLatencyStdMs = lanLatencyStdMs,
                lanLatencyMaxMs = lanLatencyMaxMs,
                wanLatencyAvgMs = wanLatencyAvgMs,
                wanLatencyStdMs = wanLatencyStdMs,
                wanLatencyMaxMs = wanLatencyMaxMs,
                jitterAvgMs = jitterAvgMs,
                jitterMaxMs = jitterMaxMs,
                rssiAvgDbm = rssiAvgDbm,
                rssiMinDbm = rssiMinDbm,
                phyRateAvgMbps = phyRateAvgMbps,
                packetLossPercent = packetLossPercent,
                diffLanWanAvgMs = diffLanWanAvgMs
            )
        }

        /**
         * Helper matemático para calcular la desviación estándar de un conjunto de datos.
         */
        private fun calculateStdDev(data: List<Float>, mean: Float): Float {
            if (data.size <= 1) return 0f
            val variance = data.fold(0f) { accum, value -> accum + (value - mean) * (value - mean) } / data.size
            return sqrt(variance)
        }
    }
}
