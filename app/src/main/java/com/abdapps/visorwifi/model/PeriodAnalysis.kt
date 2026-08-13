package com.abdapps.visorwifi.model

/**
 * Resultado del análisis estadístico de un periodo de monitoreo de red.
 *
 * Este modelo encapsula **todas** las métricas agregadas derivadas del historial de
 * mediciones. Es la entrada exclusiva para [com.abdapps.visorwifi.analysis.PeriodStatusEvaluator]
 * y para el generador de reportes PDF. **Nunca** debe mezclarse con el estado instantáneo
 * de la UI ([DiagnosisState]).
 *
 * @property periodStartMs     Timestamp Unix (ms) del primer punto de la ventana analizada.
 * @property periodEndMs       Timestamp Unix (ms) del último punto de la ventana analizada.
 * @property sampleCount       Número total de muestras en la ventana.
 *
 * — Latencia LAN (acceso local / Gateway) —
 * @property avgLanLatencyMs   Latencia LAN promedio en ms (muestras válidas, ignorando timeouts).
 * @property maxLanLatencyMs   Latencia LAN máxima registrada en ms.
 * @property lanSpikeCount     Número de muestras LAN que superaron el umbral de 50 ms.
 *
 * — Latencia WAN (acceso a Internet) —
 * @property avgWanLatencyMs   Latencia WAN promedio en ms (muestras válidas).
 * @property maxWanLatencyMs   Latencia WAN máxima registrada en ms.
 * @property minWanLatencyMs   Latencia WAN mínima registrada en ms.
 * @property latencySpikeCount Número de muestras WAN que superaron 200 ms (picos graves).
 * @property packetLossPercent Porcentaje de muestras WAN que resultaron en timeout (-1f).
 *
 * — Jitter (variabilidad temporal) —
 * @property avgJitterMs       Jitter WAN promedio en ms (diferencias absolutas consecutivas).
 * @property maxJitterMs       Jitter WAN máximo registrado en ms.
 *
 * — RSSI (Intensidad de Señal WiFi) —
 * @property rssiAvg           RSSI promedio durante el periodo en dBm.
 * @property rssiMin           RSSI mínimo (peor señal) registrado en dBm.
 * @property rssiMax           RSSI máximo (mejor señal) registrado en dBm.
 * @property rssiVariance      Varianza del RSSI en el periodo (indica estabilidad de señal).
 *
 * — Eventos de Red —
 * @property roamingEventsCount  Cantidad de eventos de roaming (cambio de BSSID/AP) detectados.
 * @property ssidChangeCount     Cantidad de cambios de red completos (cambio de SSID).
 *
 * — Puntuación de Estabilidad —
 * @property stabilityScore    Puntuación compuesta de estabilidad del enlace (0–100).
 *                             100 = enlace perfectamente estable, 0 = enlace completamente inestable.
 *                             Calculado deterministamente por [com.abdapps.visorwifi.analysis.PeriodAnalyzer].
 */
data class PeriodAnalysis(
    // ─── Metadatos del Periodo ──────────────────────────────────────────────
    val periodStartMs: Long,
    val periodEndMs: Long,
    val sampleCount: Int,

    // ─── Latencia LAN ───────────────────────────────────────────────────────
    val avgLanLatencyMs: Float,
    val maxLanLatencyMs: Float,
    val lanSpikeCount: Int,

    // ─── Latencia WAN ───────────────────────────────────────────────────────
    val avgWanLatencyMs: Float,
    val maxWanLatencyMs: Float,
    val minWanLatencyMs: Float,
    val latencySpikeCount: Int,
    val packetLossPercent: Float,

    // ─── Jitter ─────────────────────────────────────────────────────────────
    val avgJitterMs: Float,
    val maxJitterMs: Float,

    // ─── RSSI ───────────────────────────────────────────────────────────────
    val rssiAvg: Float,
    val rssiMin: Float,
    val rssiMax: Float,
    val rssiVariance: Float,

    // ─── Eventos de Red ─────────────────────────────────────────────────────
    val roamingEventsCount: Int,
    val ssidChangeCount: Int,

    // ─── Score compuesto ────────────────────────────────────────────────────
    val stabilityScore: Int
)
