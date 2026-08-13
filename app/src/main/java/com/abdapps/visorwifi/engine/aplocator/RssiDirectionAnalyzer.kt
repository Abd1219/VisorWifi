package com.abdapps.visorwifi.engine.aplocator

import com.abdapps.visorwifi.model.aplocator.DirectionEstimate
import com.abdapps.visorwifi.model.aplocator.RssiOrientationSample
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Motor de análisis de dirección de AP basado en RSSI y orientación del dispositivo.
 *
 * ## Cadena de procesamiento:
 * ```
 * RssiOrientationSample[]
 *         ↓
 * [1] Median Filter       — elimina outliers por ventana deslizante
 *         ↓
 * [2] Moving Average      — suaviza variaciones de RSSI (ventana = 5)
 *         ↓
 * [3] Angular Binning     — agrupa en sectores de 10° con wrap-around
 *         ↓
 * [4] Weighted Peak       — centroide ponderado de la zona de máxima señal
 *         ↓
 * DirectionEstimate
 * ```
 *
 * **Importante:** RSSI es negativo. -45 dBm > -70 dBm (señal más fuerte).
 * El algoritmo trabaja correctamente con esta escala en todo momento.
 *
 * Esta clase es **pura**: sin dependencias de Android SDK, sin contexto, sin efectos secundarios.
 * Es completamente determinista y testeable con JUnit estándar.
 */
object RssiDirectionAnalyzer {

    /** Tamaño de ventana para el filtro de mediana. */
    private const val MEDIAN_WINDOW = 5

    /** Tamaño de ventana para la media móvil. */
    private const val MOVING_AVG_WINDOW = 5

    /** Tamaño de cada bin angular en grados. */
    private const val BIN_DEGREES = 10

    /** Número total de bins para 360°. */
    private const val TOTAL_BINS = 360 / BIN_DEGREES

    /** Mínimo de bins ocupados para considerar cobertura suficiente. */
    private const val MIN_BINS_FOR_VALID_RESULT = 6 // 60° mínimo

    /** Umbral de rango RSSI mínimo para considerar señal diferenciada. */
    private const val MIN_RSSI_RANGE = 5

    /**
     * Analiza una lista de muestras RSSI+orientación y produce una estimación de dirección.
     *
     * @param samples Lista de muestras capturadas durante el escaneo 360°.
     * @param targetBssid BSSID que se está localizando (para detectar cambios durante la sesión).
     * @return [DirectionEstimate] con la dirección estimada y el nivel de confianza.
     */
    fun analyze(
        samples: List<RssiOrientationSample>,
        targetBssid: String
    ): DirectionEstimate {

        // Guardia: muestras insuficientes
        if (samples.size < 12) {
            return DirectionEstimate(
                azimuthDeg = 0.0,
                confidencePercent = 0,
                message = ConfidenceCalculator.getConfidenceMessage(
                    0, DirectionEstimate.WarningCode.INSUFFICIENT_SAMPLES
                ),
                bestRssi = samples.maxOfOrNull { it.rssi } ?: -127,
                avgRssi = if (samples.isEmpty()) -127f else samples.map { it.rssi }.average().toFloat(),
                warningCode = DirectionEstimate.WarningCode.INSUFFICIENT_SAMPLES
            )
        }

        // Ordenar cronológicamente para que las ventanas de mediana y media móvil sean temporales
        val timeSorted = samples.sortedBy { it.timestamp }

        // Verificar que todos los BSSID sean del AP objetivo (BSSID changed detection)
        val hasWrongBssid = timeSorted.any { it.bssid != targetBssid }
        if (hasWrongBssid) {
            return DirectionEstimate(
                azimuthDeg = 0.0,
                confidencePercent = 0,
                message = ConfidenceCalculator.getConfidenceMessage(
                    0, DirectionEstimate.WarningCode.BSSID_CHANGED
                ),
                bestRssi = -127,
                avgRssi = -127f,
                warningCode = DirectionEstimate.WarningCode.BSSID_CHANGED
            )
        }

        // Filtrar únicamente muestras del BSSID objetivo con RSSI válido
        val validSamples = timeSorted.filter {
            it.bssid == targetBssid && it.rssi > -127
        }

        if (validSamples.size < 12) {
            return DirectionEstimate(
                azimuthDeg = 0.0,
                confidencePercent = 0,
                message = ConfidenceCalculator.getConfidenceMessage(
                    0, DirectionEstimate.WarningCode.INSUFFICIENT_SAMPLES
                ),
                bestRssi = validSamples.maxOfOrNull { it.rssi } ?: -127,
                avgRssi = if (validSamples.isEmpty()) -127f
                          else validSamples.map { it.rssi }.average().toFloat(),
                warningCode = DirectionEstimate.WarningCode.INSUFFICIENT_SAMPLES
            )
        }

        val bestRssi = validSamples.maxOf { it.rssi }
        val avgRssi = validSamples.map { it.rssi }.average().toFloat()

        // ── Paso 1: Median Filter ────────────────────────────────────────────
        val medianFiltered = applyMedianFilter(validSamples, MEDIAN_WINDOW)

        // ── Paso 2: Moving Average ───────────────────────────────────────────
        val smoothed = applyMovingAverage(medianFiltered, MOVING_AVG_WINDOW)

        // ── Paso 3: Angular Binning ──────────────────────────────────────────
        val bins = buildAngularBins(smoothed)

        // Bins ocupados
        val occupiedBins = bins.count { it.isNotEmpty() }
        if (occupiedBins < MIN_BINS_FOR_VALID_RESULT) {
            val coverage = calculateCoveredDegrees(smoothed)
            return DirectionEstimate(
                azimuthDeg = 0.0,
                confidencePercent = 0,
                message = ConfidenceCalculator.getConfidenceMessage(
                    0, DirectionEstimate.WarningCode.INCOMPLETE_ROTATION
                ),
                bestRssi = bestRssi,
                avgRssi = avgRssi,
                warningCode = DirectionEstimate.WarningCode.INCOMPLETE_ROTATION
            )
        }

        // RSSI promedio por bin (solo bins ocupados)
        val binAvgRssi: Map<Int, Float> = bins.indices
            .filter { bins[it].isNotEmpty() }
            .associateWith { idx -> bins[idx].average().toFloat() }

        val allRssiValues = validSamples.map { it.rssi }
        val rssiRange = allRssiValues.max() - allRssiValues.min()

        // Señal demasiado plana (no hay dirección dominante)
        if (rssiRange < MIN_RSSI_RANGE) {
            return DirectionEstimate(
                azimuthDeg = 0.0,
                confidencePercent = 0,
                message = ConfidenceCalculator.getConfidenceMessage(
                    0, DirectionEstimate.WarningCode.FLAT_RSSI
                ),
                bestRssi = bestRssi,
                avgRssi = avgRssi,
                warningCode = DirectionEstimate.WarningCode.FLAT_RSSI
            )
        }

        // ── Paso 4: Weighted Peak ────────────────────────────────────────────
        val peakResult = findWeightedPeak(bins, binAvgRssi) ?: run {
            return DirectionEstimate(
                azimuthDeg = 0.0,
                confidencePercent = 0,
                message = ConfidenceCalculator.getConfidenceMessage(
                    0, DirectionEstimate.WarningCode.FLAT_RSSI
                ),
                bestRssi = bestRssi,
                avgRssi = avgRssi,
                warningCode = DirectionEstimate.WarningCode.FLAT_RSSI
            )
        }

        val estimatedAzimuth = peakResult.centroidDeg
        val peakBinIndices = peakResult.peakBinIndices
        val peakRssiValues = peakBinIndices.flatMap { bins[it] }.map { it.toInt() }

        // ── Confidence ───────────────────────────────────────────────────────
        val coveredDegrees = calculateCoveredDegrees(smoothed)
        val confidence = ConfidenceCalculator.calculate(
            sampleCount = validSamples.size,
            rssiValues = allRssiValues,
            peakBinRssiValues = peakRssiValues,
            coveredDegrees = coveredDegrees,
            peakBinIndices = peakBinIndices,
            allBinAvgRssi = binAvgRssi
        )

        // Determinar código de advertencia según confianza y condiciones
        val warningCode = when {
            confidence == 0 && coveredDegrees < 180.0 -> DirectionEstimate.WarningCode.INCOMPLETE_ROTATION
            confidence < 25 -> DirectionEstimate.WarningCode.UNSTABLE_RSSI
            peakRssiValues.size < 3 -> DirectionEstimate.WarningCode.TOO_FEW_PEAK_SAMPLES
            else -> DirectionEstimate.WarningCode.NONE
        }

        val message = ConfidenceCalculator.getConfidenceMessage(confidence, warningCode)

        return DirectionEstimate(
            azimuthDeg = estimatedAzimuth,
            confidencePercent = confidence,
            message = message,
            bestRssi = bestRssi,
            avgRssi = avgRssi,
            warningCode = warningCode
        )
    }

    // ─── Paso 1: Median Filter ───────────────────────────────────────────────

    /**
     * Aplica un filtro de mediana deslizante sobre los valores RSSI.
     * Los azimuts se mantienen asociados a la muestra central de la ventana.
     * Elimina outliers sin desplazar la información angular.
     */
    internal fun applyMedianFilter(
        samples: List<RssiOrientationSample>,
        windowSize: Int
    ): List<RssiOrientationSample> {
        if (samples.size < 3) return samples
        val half = windowSize / 2
        return samples.indices.map { i ->
            val start = maxOf(0, i - half)
            val end = minOf(samples.size - 1, i + half)
            val window = samples.subList(start, end + 1).map { it.rssi }.sorted()
            val medianRssi = window[window.size / 2]
            samples[i].copy(rssi = medianRssi)
        }
    }

    // ─── Paso 2: Moving Average ──────────────────────────────────────────────

    /**
     * Aplica una media móvil sobre el RSSI para suavizar variaciones rápidas.
     * El azimut no se promedia para preservar la información de posición angular.
     */
    internal fun applyMovingAverage(
        samples: List<RssiOrientationSample>,
        windowSize: Int
    ): List<RssiOrientationSample> {
        if (samples.size < 3) return samples
        val half = windowSize / 2
        return samples.indices.map { i ->
            val start = maxOf(0, i - half)
            val end = minOf(samples.size - 1, i + half)
            val avgRssi = samples.subList(start, end + 1).map { it.rssi }.average().toInt()
            samples[i].copy(rssi = avgRssi)
        }
    }

    // ─── Paso 3: Angular Binning ─────────────────────────────────────────────

    /**
     * Agrupa las muestras en 36 bins de 10° cada uno [0°, 360°).
     * Maneja el wrap-around: 355° → bin 35, 5° → bin 0.
     *
     * @return Array de 36 listas de RSSI (como Float para facilitar promediado).
     */
    internal fun buildAngularBins(samples: List<RssiOrientationSample>): Array<MutableList<Float>> {
        val bins = Array(TOTAL_BINS) { mutableListOf<Float>() }
        for (sample in samples) {
            val normalized = ((sample.azimuthDeg % 360.0) + 360.0) % 360.0
            val binIndex = (normalized / BIN_DEGREES).toInt().coerceIn(0, TOTAL_BINS - 1)
            bins[binIndex].add(sample.rssi.toFloat())
        }
        return bins
    }

    // ─── Paso 4: Weighted Peak (centroide ponderado) ─────────────────────────

    /**
     * Encuentra la zona angular de máxima señal y calcula el centroide ponderado.
     *
     * El algoritmo:
     * 1. Encuentra el bin con el RSSI promedio más alto.
     * 2. Expande la zona de pico a los bins adyacentes que superan el umbral.
     * 3. Calcula el centroide ponderado usando media circular ponderada.
     *
     * La media circular es necesaria para manejar correctamente el wrap-around
     * en picos cerca de 0° (ej. 350°, 0°, 10° → centroide ≈ 0°, no 120°).
     */
    internal fun findWeightedPeak(
        bins: Array<MutableList<Float>>,
        binAvgRssi: Map<Int, Float>
    ): PeakResult? {
        if (binAvgRssi.isEmpty()) return null

        // Bin con mayor RSSI promedio (RSSI es negativo: mayor = más fuerte = menos negativo)
        val peakBinIdx = binAvgRssi.maxByOrNull { it.value }?.key ?: return null
        val peakRssi = binAvgRssi[peakBinIdx] ?: return null

        // Umbral: bins adyacentes deben estar dentro de 10 dBm del pico para formar la zona
        val threshold = peakRssi - 10f

        // Expandir zona de pico: incluir bins adyacentes que superen el umbral
        val peakZone = mutableSetOf(peakBinIdx)
        for (offset in 1..5) {
            val rightBin = (peakBinIdx + offset) % TOTAL_BINS
            val leftBin  = (peakBinIdx - offset + TOTAL_BINS) % TOTAL_BINS

            val rightRssi = binAvgRssi[rightBin]
            val leftRssi  = binAvgRssi[leftBin]

            if (rightRssi != null && rightRssi >= threshold) peakZone.add(rightBin) else {}
            if (leftRssi != null && leftRssi >= threshold) peakZone.add(leftBin) else {}

            // Detener expansión si ambos lados han caído por debajo del umbral
            if ((rightRssi == null || rightRssi < threshold) &&
                (leftRssi == null || leftRssi < threshold)) break
        }

        // Calcular centroide circular ponderado usando sin/cos
        // Peso = RSSI desplazado al dominio positivo (sumamos 127 para que sea >= 0)
        var sinSum = 0.0
        var cosSum = 0.0
        var weightSum = 0.0

        for (binIdx in peakZone) {
            val binRssi = binAvgRssi[binIdx] ?: continue
            // Convertir RSSI negativo a peso positivo: desplazamos +127
            val weight = (binRssi + 127f).toDouble().coerceAtLeast(0.01)
            val angleDeg = binIdx * BIN_DEGREES + BIN_DEGREES / 2.0  // Centro del bin
            val angleRad = Math.toRadians(angleDeg)
            sinSum += weight * sin(angleRad)
            cosSum += weight * cos(angleRad)
            weightSum += weight
        }

        if (weightSum == 0.0) return null

        val centroidRad = atan2(sinSum / weightSum, cosSum / weightSum)
        val centroidDeg = ((Math.toDegrees(centroidRad) + 360.0) % 360.0)

        return PeakResult(centroidDeg, peakZone.sorted())
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Calcula el arco de grados realmente cubiertos por las muestras.
     * Maneja correctamente el wrap-around 359° → 0°.
     */
    internal fun calculateCoveredDegrees(samples: List<RssiOrientationSample>): Double {
        if (samples.isEmpty()) return 0.0

        // Usar bins para determinar cobertura real
        val occupiedBins = samples
            .map { (((it.azimuthDeg % 360.0) + 360.0) % 360.0 / BIN_DEGREES).toInt().coerceIn(0, TOTAL_BINS - 1) }
            .toSet()

        return occupiedBins.size * BIN_DEGREES.toDouble()
    }

    /**
     * Resultado interno de la búsqueda del pico ponderado.
     */
    data class PeakResult(
        val centroidDeg: Double,
        val peakBinIndices: List<Int>
    )
}
