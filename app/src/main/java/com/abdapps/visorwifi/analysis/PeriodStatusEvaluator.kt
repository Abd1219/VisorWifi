package com.abdapps.visorwifi.analysis

import com.abdapps.visorwifi.model.PeriodAnalysis
import com.abdapps.visorwifi.model.PeriodReportStatus

/**
 * Evaluador determinístico del estado global de un periodo de monitoreo.
 *
 * Toma un [PeriodAnalysis] ya calculado y devuelve un [PeriodReportStatus].
 * La lógica es completamente determinística: la misma entrada siempre produce
 * la misma salida. No hay estado interno ni efectos secundarios.
 *
 * **Jerarquía de decisión:**
 * La evaluación usa una estrategia de "peor caso compuesto": si cualquier
 * dimensión clave supera el umbral de una categoría, el estado no puede ser
 * mejor que esa categoría. El [stabilityScore] actúa como árbitro final
 * cuando las métricas individuales son ambiguas.
 *
 * **Por qué NO usamos [DiagnosisStatus] del ViewModel:**
 * [DiagnosisStatus] refleja el estado de la última ventana de ~300 muestras
 * ponderada en tiempo real. Puede ser HEALTHY incluso si durante el periodo
 * hubo 3 minutos de PROBLEM seguidos de 2 minutos de HEALTHY. El PDF debe
 * representar la experiencia completa del cliente, no solo el último instante.
 */
object PeriodStatusEvaluator {

    // ─── Umbrales por métrica y nivel ────────────────────────────────────────
    //
    // Cada bloque documenta los rangos que clasifican cada nivel.
    // La lógica evalúa de más severo a menos severo (CRITICAL → OPTIMAL).

    // Stability Score
    private const val SCORE_OPTIMAL   = 80
    private const val SCORE_DEGRADED  = 60
    private const val SCORE_UNSTABLE  = 40
    // < SCORE_UNSTABLE → CRITICAL

    // WAN avg latency (ms)
    private const val WAN_AVG_DEGRADED  = 100f
    private const val WAN_AVG_UNSTABLE  = 150f
    private const val WAN_AVG_CRITICAL  = 200f

    // WAN spikes count (muestras > 200 ms)
    private const val SPIKES_DEGRADED  = 2
    private const val SPIKES_UNSTABLE  = 5
    private const val SPIKES_CRITICAL  = 10

    // Packet loss (%)
    private const val LOSS_DEGRADED  = 2f
    private const val LOSS_UNSTABLE  = 5f
    private const val LOSS_CRITICAL  = 10f

    // RSSI mínimo del periodo (dBm)
    private const val RSSI_MIN_DEGRADED = -72
    private const val RSSI_MIN_UNSTABLE = -80
    // < RSSI_MIN_UNSTABLE → CRITICAL

    // Roaming events count
    private const val ROAMING_DEGRADED  = 1
    private const val ROAMING_UNSTABLE  = 3
    private const val ROAMING_CRITICAL  = 5

    /**
     * Evalúa el estado global del periodo analizado.
     *
     * @param analysis Análisis estadístico previo del historial de mediciones.
     * @return [PeriodReportStatus] que representa la conclusión del periodo.
     */
    fun evaluate(analysis: PeriodAnalysis): PeriodReportStatus {
        // Sin datos suficientes: estado indeterminado → DEGRADED como postura conservadora
        if (analysis.sampleCount < 10) return PeriodReportStatus.DEGRADED

        // Calcular el nivel mínimo requerido por cada dimensión independiente
        val statusByScore    = evaluateByScore(analysis.stabilityScore)
        val statusByWanAvg   = evaluateByWanAvg(analysis.avgWanLatencyMs)
        val statusBySpikes   = evaluateBySpikes(analysis.latencySpikeCount)
        val statusByLoss     = evaluateByPacketLoss(analysis.packetLossPercent)
        val statusByRssi     = evaluateByRssi(analysis.rssiMin)
        val statusByRoaming  = evaluateByRoaming(analysis.roamingEventsCount)

        // El estado global es el peor (más severo) de todas las dimensiones
        val worstStatus = listOf(
            statusByScore,
            statusByWanAvg,
            statusBySpikes,
            statusByLoss,
            statusByRssi,
            statusByRoaming
        ).maxByOrNull { it.severity() } ?: PeriodReportStatus.OPTIMAL

        return worstStatus
    }

    /**
     * Devuelve un mapa de contribuciones por dimensión para incluir en el PDF
     * como explicación técnica del estado asignado.
     *
     * @param analysis Análisis del periodo.
     * @return Lista de pares (dimensión, nivel) ordenados de más severo a menos.
     */
    fun evaluateWithDetail(analysis: PeriodAnalysis): EvaluationDetail {
        val overall = evaluate(analysis)
        val dimensions = mapOf(
            "Score de Estabilidad (${analysis.stabilityScore}/100)"
                to evaluateByScore(analysis.stabilityScore),
            "Latencia WAN prom. (${"%.1f".format(analysis.avgWanLatencyMs)} ms)"
                to evaluateByWanAvg(analysis.avgWanLatencyMs),
            "Picos WAN > 200 ms (${analysis.latencySpikeCount} eventos)"
                to evaluateBySpikes(analysis.latencySpikeCount),
            "Pérdida de paquetes (${"%.1f".format(analysis.packetLossPercent)}%)"
                to evaluateByPacketLoss(analysis.packetLossPercent),
            "RSSI mínimo (${analysis.rssiMin.toInt()} dBm)"
                to evaluateByRssi(analysis.rssiMin),
            "Roaming / Cambios de AP (${analysis.roamingEventsCount})"
                to evaluateByRoaming(analysis.roamingEventsCount)
        )
        return EvaluationDetail(overallStatus = overall, dimensionStatuses = dimensions)
    }

    // ─── Evaluadores por dimensión ────────────────────────────────────────────

    private fun evaluateByScore(score: Int): PeriodReportStatus = when {
        score >= SCORE_OPTIMAL   -> PeriodReportStatus.OPTIMAL
        score >= SCORE_DEGRADED  -> PeriodReportStatus.DEGRADED
        score >= SCORE_UNSTABLE  -> PeriodReportStatus.UNSTABLE
        else                     -> PeriodReportStatus.CRITICAL
    }

    private fun evaluateByWanAvg(avgMs: Float): PeriodReportStatus = when {
        avgMs <= 0f                -> PeriodReportStatus.OPTIMAL  // Sin datos WAN válidos
        avgMs < WAN_AVG_DEGRADED   -> PeriodReportStatus.OPTIMAL
        avgMs < WAN_AVG_UNSTABLE   -> PeriodReportStatus.DEGRADED
        avgMs < WAN_AVG_CRITICAL   -> PeriodReportStatus.UNSTABLE
        else                       -> PeriodReportStatus.CRITICAL
    }

    private fun evaluateBySpikes(count: Int): PeriodReportStatus = when {
        count < SPIKES_DEGRADED -> PeriodReportStatus.OPTIMAL
        count < SPIKES_UNSTABLE -> PeriodReportStatus.DEGRADED
        count < SPIKES_CRITICAL -> PeriodReportStatus.UNSTABLE
        else                    -> PeriodReportStatus.CRITICAL
    }

    private fun evaluateByPacketLoss(loss: Float): PeriodReportStatus = when {
        loss < LOSS_DEGRADED -> PeriodReportStatus.OPTIMAL
        loss < LOSS_UNSTABLE -> PeriodReportStatus.DEGRADED
        loss < LOSS_CRITICAL -> PeriodReportStatus.UNSTABLE
        else                 -> PeriodReportStatus.CRITICAL
    }

    private fun evaluateByRssi(rssiMin: Float): PeriodReportStatus = when {
        rssiMin <= -127f                   -> PeriodReportStatus.OPTIMAL  // Sin datos RSSI
        rssiMin >= RSSI_MIN_DEGRADED       -> PeriodReportStatus.OPTIMAL
        rssiMin >= RSSI_MIN_UNSTABLE       -> PeriodReportStatus.DEGRADED
        rssiMin >= -90                     -> PeriodReportStatus.UNSTABLE
        else                               -> PeriodReportStatus.CRITICAL
    }

    private fun evaluateByRoaming(count: Int): PeriodReportStatus = when {
        count < ROAMING_DEGRADED -> PeriodReportStatus.OPTIMAL
        count < ROAMING_UNSTABLE -> PeriodReportStatus.DEGRADED
        count < ROAMING_CRITICAL -> PeriodReportStatus.UNSTABLE
        else                     -> PeriodReportStatus.CRITICAL
    }
}

// ─── Modelos de retorno ───────────────────────────────────────────────────────

/**
 * Resultado detallado de la evaluación del periodo, incluyendo el estado por cada
 * dimensión evaluada. Útil para el PDF y para auditoría de la decisión.
 */
data class EvaluationDetail(
    val overallStatus: PeriodReportStatus,
    val dimensionStatuses: Map<String, PeriodReportStatus>
)

/** Ordinal numérico de severidad para comparar estados entre sí. */
private fun PeriodReportStatus.severity(): Int = when (this) {
    PeriodReportStatus.OPTIMAL   -> 0
    PeriodReportStatus.DEGRADED  -> 1
    PeriodReportStatus.UNSTABLE  -> 2
    PeriodReportStatus.CRITICAL  -> 3
}
