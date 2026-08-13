package com.abdapps.visorwifi.analysis

import com.abdapps.visorwifi.model.LatencyPoint
import com.abdapps.visorwifi.model.PeriodAnalysis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Analizador estadístico de un periodo de telemetría de red.
 *
 * Recibe el historial completo de puntos de medición y calcula todas las métricas
 * agregadas necesarias para la evaluación del reporte PDF.
 *
 * **Diseño deliberado:**
 * - Sin dependencias de Android (testeable con JUnit puro).
 * - Sin efectos secundarios: función pura, misma entrada → misma salida.
 * - Desacoplado de la lógica de UI y del diagnóstico en tiempo real.
 */
object PeriodAnalyzer {

    // ─── Umbrales de picos ───────────────────────────────────────────────────

    /** Umbral de latencia LAN para considerar una muestra como "pico local" (ms). */
    private const val LAN_SPIKE_THRESHOLD_MS = 50f

    /** Umbral de latencia WAN para considerar una muestra como "pico de internet" (ms). */
    private const val WAN_SPIKE_THRESHOLD_MS = 200f

    // ─── Pesos para el stabilityScore (deben sumar 100) ─────────────────────

    private const val WEIGHT_WAN_AVG       = 25   // Latencia WAN promedio
    private const val WEIGHT_WAN_SPIKES    = 20   // Picos de latencia WAN graves
    private const val WEIGHT_PACKET_LOSS   = 20   // Pérdida de paquetes WAN
    private const val WEIGHT_LAN_AVG       = 15   // Latencia LAN promedio
    private const val WEIGHT_RSSI_MIN      = 12   // Peor RSSI del periodo
    private const val WEIGHT_JITTER_AVG    = 8    // Jitter promedio WAN

    // Suma total de pesos = 100

    /**
     * Analiza el historial de mediciones y produce un [PeriodAnalysis] completo.
     *
     * Si la lista está vacía o tiene menos de 2 muestras, devuelve un análisis
     * con todos los campos a 0 / valores por defecto para evitar crashes.
     *
     * @param history Lista de puntos de latencia ordenados cronológicamente.
     * @param networkEvents Lista de eventos de red en formato texto para contar roaming/SSID changes.
     * @return Instancia [PeriodAnalysis] con todas las métricas calculadas.
     */
    fun analyze(
        history: List<LatencyPoint>,
        networkEvents: List<String> = emptyList()
    ): PeriodAnalysis {
        if (history.isEmpty()) {
            return emptyAnalysis()
        }

        val periodStartMs = history.first().timestamp
        val periodEndMs   = history.last().timestamp
        val sampleCount   = history.size

        // ─── Latencia LAN ────────────────────────────────────────────────────
        val lanValid = history.map { it.lanLatency }.filter { it >= 0f }
        val avgLanLatencyMs  = lanValid.averageOrZero()
        val maxLanLatencyMs  = lanValid.maxOrElse(0f)
        val lanSpikeCount    = lanValid.count { it > LAN_SPIKE_THRESHOLD_MS }

        // ─── Latencia WAN ────────────────────────────────────────────────────
        val wanValid = history.map { it.wanLatency }.filter { it >= 0f }
        val avgWanLatencyMs   = wanValid.averageOrZero()
        val maxWanLatencyMs   = wanValid.maxOrElse(0f)
        val minWanLatencyMs   = wanValid.minOrElse(0f)
        val latencySpikeCount = wanValid.count { it > WAN_SPIKE_THRESHOLD_MS }

        val wanTimeouts       = history.count { it.wanLatency < 0f }
        val packetLossPercent = (wanTimeouts.toFloat() / sampleCount) * 100f

        // ─── Jitter WAN ──────────────────────────────────────────────────────
        val jitterValues = buildJitterList(history)
        val avgJitterMs  = jitterValues.averageOrZero()
        val maxJitterMs  = jitterValues.maxOrElse(0f)

        // ─── RSSI ─────────────────────────────────────────────────────────────
        val rssiPoints  = history.map { it.rssi.toFloat() }.filter { it > -127f }
        val rssiAvg     = rssiPoints.averageOrElse(-127f)
        val rssiMin     = rssiPoints.minOrElse(-127f)
        val rssiMax     = rssiPoints.maxOrElse(-127f)
        val rssiVariance = calculateVariance(rssiPoints, rssiAvg)

        // ─── Eventos de Red ───────────────────────────────────────────────────
        val roamingEventsCount = networkEvents.count {
            it.contains("Roaming", ignoreCase = true)
        }
        val ssidChangeCount = networkEvents.count {
            it.contains("Cambio de RED", ignoreCase = true) ||
            it.contains("RED:", ignoreCase = true)
        }

        // ─── Stability Score ──────────────────────────────────────────────────
        val stabilityScore = calculateStabilityScore(
            avgWanLatencyMs  = avgWanLatencyMs,
            latencySpikeCount = latencySpikeCount,
            packetLossPercent = packetLossPercent,
            avgLanLatencyMs  = avgLanLatencyMs,
            rssiMin          = rssiMin,
            avgJitterMs      = avgJitterMs,
            sampleCount      = sampleCount
        )

        return PeriodAnalysis(
            periodStartMs     = periodStartMs,
            periodEndMs       = periodEndMs,
            sampleCount       = sampleCount,
            avgLanLatencyMs   = avgLanLatencyMs,
            maxLanLatencyMs   = maxLanLatencyMs,
            lanSpikeCount     = lanSpikeCount,
            avgWanLatencyMs   = avgWanLatencyMs,
            maxWanLatencyMs   = maxWanLatencyMs,
            minWanLatencyMs   = minWanLatencyMs,
            latencySpikeCount = latencySpikeCount,
            packetLossPercent = packetLossPercent,
            avgJitterMs       = avgJitterMs,
            maxJitterMs       = maxJitterMs,
            rssiAvg           = rssiAvg,
            rssiMin           = rssiMin,
            rssiMax           = rssiMax,
            rssiVariance      = rssiVariance,
            roamingEventsCount = roamingEventsCount,
            ssidChangeCount   = ssidChangeCount,
            stabilityScore    = stabilityScore
        )
    }

    // ─── Cálculo del Stability Score ─────────────────────────────────────────

    /**
     * Calcula el score de estabilidad compuesto (0–100) ponderando cada métrica.
     *
     * Cada componente produce un sub-score parcial entre 0 y su peso máximo.
     * La suma de todos los pesos es 100, por lo que el score total es directamente
     * interpretable como un porcentaje de "calidad del periodo".
     *
     * **Umbrales documentados:**
     * - WAN avg < 80 ms  → excelente; > 250 ms  → muy malo
     * - Picos WAN 0      → máximo; ≥ 10         → mínimo (ajustado por proporción)
     * - Pérdida 0%       → máximo; ≥ 15%         → cero
     * - LAN avg < 10 ms  → máximo; > 60 ms       → cero
     * - RSSI mínimo ≥ -55 dBm → excelente; < -85 dBm → cero
     * - Jitter < 15 ms   → máximo; > 80 ms       → cero
     */
    private fun calculateStabilityScore(
        avgWanLatencyMs: Float,
        latencySpikeCount: Int,
        packetLossPercent: Float,
        avgLanLatencyMs: Float,
        rssiMin: Float,
        avgJitterMs: Float,
        sampleCount: Int
    ): Int {
        // Componente 1: Latencia WAN promedio (peso 25)
        val wanAvgScore = when {
            avgWanLatencyMs <= 0f   -> WEIGHT_WAN_AVG  // Sin datos WAN válidos: score neutro
            avgWanLatencyMs < 80f   -> WEIGHT_WAN_AVG
            avgWanLatencyMs < 120f  -> (WEIGHT_WAN_AVG * 0.80f).toInt()
            avgWanLatencyMs < 160f  -> (WEIGHT_WAN_AVG * 0.60f).toInt()
            avgWanLatencyMs < 200f  -> (WEIGHT_WAN_AVG * 0.35f).toInt()
            avgWanLatencyMs < 250f  -> (WEIGHT_WAN_AVG * 0.15f).toInt()
            else                    -> 0
        }

        // Componente 2: Picos WAN graves (peso 20) — proporcional a muestras totales
        val spikeRatio = if (sampleCount > 0) latencySpikeCount.toFloat() / sampleCount else 0f
        val spikeScore = when {
            spikeRatio < 0.01f  -> WEIGHT_WAN_SPIKES
            spikeRatio < 0.03f  -> (WEIGHT_WAN_SPIKES * 0.75f).toInt()
            spikeRatio < 0.06f  -> (WEIGHT_WAN_SPIKES * 0.50f).toInt()
            spikeRatio < 0.12f  -> (WEIGHT_WAN_SPIKES * 0.25f).toInt()
            spikeRatio < 0.20f  -> (WEIGHT_WAN_SPIKES * 0.10f).toInt()
            else                -> 0
        }

        // Componente 3: Pérdida de paquetes WAN (peso 20)
        val lossScore = when {
            packetLossPercent < 1f   -> WEIGHT_PACKET_LOSS
            packetLossPercent < 3f   -> (WEIGHT_PACKET_LOSS * 0.75f).toInt()
            packetLossPercent < 6f   -> (WEIGHT_PACKET_LOSS * 0.50f).toInt()
            packetLossPercent < 10f  -> (WEIGHT_PACKET_LOSS * 0.25f).toInt()
            packetLossPercent < 15f  -> (WEIGHT_PACKET_LOSS * 0.10f).toInt()
            else                     -> 0
        }

        // Componente 4: Latencia LAN promedio (peso 15)
        val lanAvgScore = when {
            avgLanLatencyMs <= 0f  -> WEIGHT_LAN_AVG  // Sin datos LAN: score neutro
            avgLanLatencyMs < 10f  -> WEIGHT_LAN_AVG
            avgLanLatencyMs < 20f  -> (WEIGHT_LAN_AVG * 0.80f).toInt()
            avgLanLatencyMs < 35f  -> (WEIGHT_LAN_AVG * 0.55f).toInt()
            avgLanLatencyMs < 60f  -> (WEIGHT_LAN_AVG * 0.25f).toInt()
            else                   -> 0
        }

        // Componente 5: RSSI mínimo del periodo (peso 12)
        val rssiScore = when {
            rssiMin > -127f && rssiMin >= -55f  -> WEIGHT_RSSI_MIN
            rssiMin > -127f && rssiMin >= -62f  -> (WEIGHT_RSSI_MIN * 0.80f).toInt()
            rssiMin > -127f && rssiMin >= -70f  -> (WEIGHT_RSSI_MIN * 0.55f).toInt()
            rssiMin > -127f && rssiMin >= -78f  -> (WEIGHT_RSSI_MIN * 0.30f).toInt()
            rssiMin > -127f && rssiMin >= -85f  -> (WEIGHT_RSSI_MIN * 0.10f).toInt()
            else                                -> 0  // Sin señal o -127 (sin datos)
        }

        // Componente 6: Jitter WAN promedio (peso 8)
        val jitterScore = when {
            avgJitterMs < 15f  -> WEIGHT_JITTER_AVG
            avgJitterMs < 30f  -> (WEIGHT_JITTER_AVG * 0.70f).toInt()
            avgJitterMs < 50f  -> (WEIGHT_JITTER_AVG * 0.40f).toInt()
            avgJitterMs < 80f  -> (WEIGHT_JITTER_AVG * 0.15f).toInt()
            else               -> 0
        }

        val raw = wanAvgScore + spikeScore + lossScore + lanAvgScore + rssiScore + jitterScore
        return raw.coerceIn(0, 100)
    }

    // ─── Helpers matemáticos ─────────────────────────────────────────────────

    /** Calcula las diferencias absolutas consecutivas de latencia WAN (jitter). */
    private fun buildJitterList(history: List<LatencyPoint>): List<Float> {
        val result = mutableListOf<Float>()
        for (i in 1 until history.size) {
            val p1 = history[i - 1].wanLatency
            val p2 = history[i].wanLatency
            if (p1 >= 0f && p2 >= 0f) {
                result.add(abs(p2 - p1))
            }
        }
        return result
    }

    private fun calculateVariance(data: List<Float>, mean: Float): Float {
        if (data.size <= 1) return 0f
        return data.fold(0f) { acc, v -> acc + (v - mean) * (v - mean) } / data.size
    }

    private fun List<Float>.averageOrZero(): Float =
        if (isEmpty()) 0f else average().toFloat()

    private fun List<Float>.averageOrElse(default: Float): Float =
        if (isEmpty()) default else average().toFloat()

    private fun List<Float>.maxOrElse(default: Float): Float =
        if (isEmpty()) default else maxOrNull() ?: default

    private fun List<Float>.minOrElse(default: Float): Float =
        if (isEmpty()) default else minOrNull() ?: default

    /** Análisis vacío para sesiones sin datos suficientes. */
    private fun emptyAnalysis() = PeriodAnalysis(
        periodStartMs     = System.currentTimeMillis(),
        periodEndMs       = System.currentTimeMillis(),
        sampleCount       = 0,
        avgLanLatencyMs   = 0f,
        maxLanLatencyMs   = 0f,
        lanSpikeCount     = 0,
        avgWanLatencyMs   = 0f,
        maxWanLatencyMs   = 0f,
        minWanLatencyMs   = 0f,
        latencySpikeCount = 0,
        packetLossPercent = 0f,
        avgJitterMs       = 0f,
        maxJitterMs       = 0f,
        rssiAvg           = -127f,
        rssiMin           = -127f,
        rssiMax           = -127f,
        rssiVariance      = 0f,
        roamingEventsCount = 0,
        ssidChangeCount   = 0,
        stabilityScore    = 0
    )
}
