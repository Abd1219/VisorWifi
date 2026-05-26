package com.abdapps.visorwifi.engine

import com.abdapps.visorwifi.model.DiagnosisState
import com.abdapps.visorwifi.model.DiagnosisStatus
import com.abdapps.visorwifi.model.NetworkMetrics
import java.util.Locale

/**
 * Motor de diagnóstico de red basado en reglas heurísticas (Fase 1).
 *
 * Esta clase analiza las métricas de red consolidadas de [NetworkMetrics] y
 * produce un estado de diagnóstico cualitativo descriptivo y accionable.
 *
 * El motor está desacoplado de la UI y del ciclo de vida de Android para permitir
 * que en futuras fases de desarrollo se reemplace o extienda con un motor de IA/ML.
 */
object NetworkDiagnosisEngine {

    /**
     * Evalúa las métricas de red y produce un estado de diagnóstico.
     *
     * @param metrics Métricas consolidadas de red (LAN, WAN, Jitter, RSSI, pérdida de paquetes).
     * @return El estado de diagnóstico [DiagnosisState] correspondiente.
     */
    fun evaluateDiagnosis(metrics: NetworkMetrics): DiagnosisState {
        // Reglas de negocio y umbrales heurísticos
        val isLanStable = metrics.lanLatencyAvgMs < 15f && metrics.lanLatencyMaxMs < 50f
        val hasWanSpikes = metrics.wanLatencyMaxMs > 200f || metrics.wanLatencyAvgMs > 120f || (metrics.packetLossPercent ?: 0f) > 5f
        val hasLanSpikes = metrics.lanLatencyMaxMs > 100f
        val isRssiWeak = metrics.rssiDbm < -65
        val isRssiGood = metrics.rssiDbm >= -65
        val hasHighJitter = metrics.jitterMs > 30f

        // Caso 1: Latencia LAN es estable pero la latencia WAN presenta picos altos o pérdida
        if (isLanStable && hasWanSpikes) {
            val lossText = metrics.packetLossPercent?.let { ", Pérdida: %.1f%%".format(Locale.US, it) } ?: ""
            return DiagnosisState(
                status = DiagnosisStatus.PROBLEM,
                title = "Posible problema del proveedor de internet",
                confidence = 85,
                evidences = listOf(
                    "Acceso local estable: %.1f ms avg".format(Locale.US, metrics.lanLatencyAvgMs),
                    "Internet (WAN) inestable: %.1f ms max (avg: %.1f ms)%s".format(Locale.US, metrics.wanLatencyMaxMs, metrics.wanLatencyAvgMs, lossText),
                    "Fluctuaciones anormales fuera de la red local"
                ),
                recommendation = "Recomendación: Contactar a tu proveedor de internet (ISP) o reiniciar el módem/router"
            )
        }

        // Caso 2: Latencia LAN presenta picos frecuentes y el RSSI es débil (< -65 dBm)
        if (hasLanSpikes && isRssiWeak) {
            return DiagnosisState(
                status = DiagnosisStatus.PROBLEM,
                title = "Interferencia o señal Wi-Fi débil",
                confidence = 90,
                evidences = listOf(
                    "Intensidad de señal deficiente: ${metrics.rssiDbm} dBm",
                    "Picos de latencia local elevados: %.1f ms max".format(Locale.US, metrics.lanLatencyMaxMs),
                    "Velocidad de enlace física (PHY): ${metrics.phyRateMbps} Mbps"
                ),
                recommendation = "Recomendación: Acércate al router Wi-Fi o cámbiate a una banda menos saturada (5 GHz / 6 GHz)"
            )
        }

        // Caso 3: Jitter elevado (> 30 ms) aunque el RSSI sea adecuado (>= -65 dBm)
        if (hasHighJitter && isRssiGood) {
            return DiagnosisState(
                status = DiagnosisStatus.WARNING,
                title = "Calidad de red inestable",
                confidence = 75,
                evidences = listOf(
                    "Variación de latencia (Jitter) alta: %.1f ms".format(Locale.US, metrics.jitterMs),
                    "Señal Wi-Fi adecuada: ${metrics.rssiDbm} dBm",
                    "Velocidad de enlace actual: ${metrics.phyRateMbps} Mbps"
                ),
                recommendation = "Recomendación: Intenta reducir el tráfico de red en tu dispositivo o cambiar el canal Wi-Fi"
            )
        }

        // Caso 4: Todo normal (Red saludable)
        return DiagnosisState(
            status = DiagnosisStatus.HEALTHY,
            title = "Red saludable",
            confidence = 95,
            evidences = listOf(
                "Latencia local estable: %.1f ms avg".format(Locale.US, metrics.lanLatencyAvgMs),
                "Conexión a internet estable: %.1f ms avg".format(Locale.US, metrics.wanLatencyAvgMs),
                "Intensidad de señal fuerte: ${metrics.rssiDbm} dBm"
            ),
            recommendation = "Recomendación: Tu conexión está funcionando en condiciones óptimas"
        )
    }
}
