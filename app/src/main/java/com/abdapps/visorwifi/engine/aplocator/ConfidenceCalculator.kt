package com.abdapps.visorwifi.engine.aplocator

import com.abdapps.visorwifi.model.aplocator.DirectionEstimate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Calcula la confianza de una estimación de dirección de AP basándose en cinco factores ponderados.
 *
 * ## Factores y pesos:
 * | Factor                | Peso |
 * |----------------------|------|
 * | Cantidad de muestras  |  20% |
 * | Rango RSSI (max-min)  |  25% |
 * | Varianza zona de pico |  20% |
 * | Cobertura angular     |  20% |
 * | Consistencia del pico |  15% |
 *
 * Esta clase es pura: sin dependencias de Android SDK y sin efectos secundarios.
 */
object ConfidenceCalculator {

    // Umbrales y constantes
    private const val MIN_SAMPLES = 12
    private const val OPTIMAL_SAMPLES = 36
    private const val MIN_RSSI_RANGE_DBM = 5      // Variación mínima para considerar direccionalidad
    private const val GOOD_RSSI_RANGE_DBM = 15    // Variación que indica señal claramente diferenciada
    private const val EXCELLENT_RSSI_RANGE_DBM = 25
    private const val BIN_DEGREES = 10
    private const val TOTAL_BINS = 360 / BIN_DEGREES // 36 bins

    // Pesos (deben sumar 100)
    private const val WEIGHT_SAMPLE_COUNT = 20
    private const val WEIGHT_RSSI_RANGE = 25
    private const val WEIGHT_PEAK_VARIANCE = 20
    private const val WEIGHT_ANGULAR_COVERAGE = 20
    private const val WEIGHT_PEAK_CONSISTENCY = 15

    /**
     * Calcula el confidence score [0–100] a partir de los datos del escaneo.
     *
     * @param sampleCount        Número total de muestras capturadas.
     * @param rssiValues         Lista de valores RSSI de todas las muestras (en dBm, negativos).
     * @param peakBinRssiValues  Lista de RSSI de las muestras en la zona del pico estimado.
     * @param coveredDegrees     Grados angulares realmente recorridos durante el escaneo [0, 360].
     * @param peakBinIndices     Índices de bins que conforman la zona de pico.
     * @param allBinAvgRssi      RSSI promedio por bin (36 bins de 10° cada uno, null si vacío).
     * @return Porcentaje de confianza entre 0 y 100.
     */
    fun calculate(
        sampleCount: Int,
        rssiValues: List<Int>,
        peakBinRssiValues: List<Int>,
        coveredDegrees: Double,
        peakBinIndices: List<Int>,
        allBinAvgRssi: Map<Int, Float>
    ): Int {
        // Umbral duro: sin suficientes muestras la confianza es 0
        if (sampleCount < MIN_SAMPLES) return 0
        if (rssiValues.isEmpty()) return 0

        val sampleScore    = calculateSampleScore(sampleCount)
        val rssiRangeScore = calculateRssiRangeScore(rssiValues)
        val varianceScore  = calculatePeakVarianceScore(peakBinRssiValues)
        val coverageScore  = calculateAngularCoverageScore(coveredDegrees, sampleCount)
        val consistencyScore = calculatePeakConsistencyScore(peakBinIndices, allBinAvgRssi)

        val rawTotal = sampleScore + rssiRangeScore + varianceScore + coverageScore + consistencyScore
        val coverageScale = if (coveredDegrees < 180.0) (coveredDegrees / 180.0).coerceIn(0.0, 1.0) else 1.0
        val total = (rawTotal * coverageScale).toInt()
        return total.coerceIn(0, 100)
    }

    // ─── Factor 1: Cantidad de muestras (peso 20) ────────────────────────────

    internal fun calculateSampleScore(sampleCount: Int): Int {
        return when {
            sampleCount < MIN_SAMPLES     -> 0
            sampleCount >= OPTIMAL_SAMPLES -> WEIGHT_SAMPLE_COUNT
            else -> {
                val ratio = (sampleCount - MIN_SAMPLES).toFloat() / (OPTIMAL_SAMPLES - MIN_SAMPLES)
                (WEIGHT_SAMPLE_COUNT * ratio).toInt()
            }
        }
    }

    // ─── Factor 2: Rango RSSI max-min (peso 25) ──────────────────────────────

    /**
     * Rango RSSI = maxRssi - minRssi.
     * RSSI es negativo: -45 > -70, por tanto max=-45, min=-70, rango=25 dBm.
     */
    internal fun calculateRssiRangeScore(rssiValues: List<Int>): Int {
        if (rssiValues.size < 2) return 0
        val maxRssi = rssiValues.max()
        val minRssi = rssiValues.min()
        val range = maxRssi - minRssi // siempre >= 0

        return when {
            range < MIN_RSSI_RANGE_DBM        -> 0
            range < GOOD_RSSI_RANGE_DBM       -> (WEIGHT_RSSI_RANGE * 0.4f).toInt()
            range < EXCELLENT_RSSI_RANGE_DBM  -> (WEIGHT_RSSI_RANGE * 0.75f).toInt()
            else                              -> WEIGHT_RSSI_RANGE
        }
    }

    // ─── Factor 3: Varianza de la zona de pico (peso 20) ─────────────────────

    /**
     * Una zona de pico con poca varianza indica que la señal fuerte es consistente.
     * Una varianza alta en el pico (ej. un outlier) reduce la confianza.
     */
    internal fun calculatePeakVarianceScore(peakBinRssiValues: List<Int>): Int {
        if (peakBinRssiValues.isEmpty()) return 0
        if (peakBinRssiValues.size == 1) return (WEIGHT_PEAK_VARIANCE * 0.3f).toInt()

        val mean = peakBinRssiValues.average()
        val variance = peakBinRssiValues.sumOf { (it - mean) * (it - mean) } / peakBinRssiValues.size
        val stdDev = sqrt(variance)

        return when {
            stdDev < 2.0  -> WEIGHT_PEAK_VARIANCE                            // Muy consistente
            stdDev < 4.0  -> (WEIGHT_PEAK_VARIANCE * 0.75f).toInt()
            stdDev < 7.0  -> (WEIGHT_PEAK_VARIANCE * 0.45f).toInt()
            stdDev < 12.0 -> (WEIGHT_PEAK_VARIANCE * 0.15f).toInt()
            else          -> 0                                                // Demasiado disperso
        }
    }

    // ─── Factor 4: Cobertura angular (peso 20) ───────────────────────────────

    /**
     * Verifica que realmente se haya cubierto suficiente arco de rotación Y
     * que haya muestras distribuidas (no solo muchas muestras en un único sector).
     */
    internal fun calculateAngularCoverageScore(coveredDegrees: Double, sampleCount: Int): Int {
        // Necesitamos al menos 180° de cobertura para una estimación mínimamente válida
        val coverageRatio = (coveredDegrees / 360.0).coerceIn(0.0, 1.0)
        val coverageComponent = (WEIGHT_ANGULAR_COVERAGE * coverageRatio).toInt()

        // Penalizar si hay pocas muestras para los grados cubiertos (giro demasiado rápido)
        val expectedSamplesForCoverage = (coveredDegrees / 10.0).toInt() // ~1 muestra por 10°
        val densityRatio = if (expectedSamplesForCoverage > 0)
            (sampleCount.toFloat() / expectedSamplesForCoverage).coerceIn(0f, 1f)
        else 0f

        return (coverageComponent * densityRatio).toInt()
    }

    // ─── Factor 5: Consistencia del pico (peso 15) ───────────────────────────

    /**
     * Verifica si la zona de pico es realmente una zona extendida y no solo
     * un único bin con un valor aisladamente alto.
     *
     * Un pico real suele manifestarse en 3+ bins consecutivos con señal fuerte.
     */
    internal fun calculatePeakConsistencyScore(
        peakBinIndices: List<Int>,
        allBinAvgRssi: Map<Int, Float>
    ): Int {
        if (peakBinIndices.isEmpty() || allBinAvgRssi.isEmpty()) return 0

        val peakAvgRssi = peakBinIndices.mapNotNull { allBinAvgRssi[it] }.average().toFloat()
        val globalAvgRssi = allBinAvgRssi.values.average().toFloat()

        // Diferencia entre la zona de pico y el promedio global (positivo = pico más fuerte)
        val peakAdvantage = peakAvgRssi - globalAvgRssi // RSSI negativo: pico > global

        // Tamaño de la zona de pico: más bins contiguos = mayor confianza
        val peakWidth = peakBinIndices.size

        val widthScore = when {
            peakWidth >= 4 -> 1.0f
            peakWidth == 3 -> 0.80f
            peakWidth == 2 -> 0.55f
            peakWidth == 1 -> 0.25f
            else           -> 0f
        }

        // peakAdvantage es negativo si el pico es más fuerte (ej. -48 - -60 = +12 positivo)
        val advantageScore = when {
            peakAdvantage >= 10f  -> 1.0f
            peakAdvantage >= 6f   -> 0.75f
            peakAdvantage >= 3f   -> 0.50f
            peakAdvantage >= 1f   -> 0.25f
            else                  -> 0f
        }

        return (WEIGHT_PEAK_CONSISTENCY * widthScore * 0.5f + WEIGHT_PEAK_CONSISTENCY * advantageScore * 0.5f).toInt()
    }

    /**
     * Genera un mensaje descriptivo basado en el porcentaje de confianza.
     */
    fun getConfidenceMessage(confidence: Int, warningCode: DirectionEstimate.WarningCode): String {
        return when (warningCode) {
            DirectionEstimate.WarningCode.INSUFFICIENT_SAMPLES ->
                "Se necesitan más muestras. Gira más lentamente."
            DirectionEstimate.WarningCode.FLAT_RSSI ->
                "No se detectó una dirección dominante. La señal es muy uniforme."
            DirectionEstimate.WarningCode.UNSTABLE_RSSI ->
                "La señal presenta demasiadas variaciones. La estimación puede ser imprecisa."
            DirectionEstimate.WarningCode.INCOMPLETE_ROTATION ->
                "Cobertura angular insuficiente. Intenta girar más para cubrir 360°."
            DirectionEstimate.WarningCode.BSSID_CHANGED ->
                "La conexión cambió de AP. Reinicia la medición."
            DirectionEstimate.WarningCode.SENSOR_UNAVAILABLE ->
                "El dispositivo no dispone del sensor necesario para esta medición."
            DirectionEstimate.WarningCode.TOO_FEW_PEAK_SAMPLES ->
                "La zona de máxima señal tiene muy pocas muestras. Repite el escaneo."
            DirectionEstimate.WarningCode.NONE -> when {
                confidence >= 80 -> "Dirección bastante consistente"
                confidence >= 60 -> "Estimación razonable"
                confidence >= 40 -> "Estimación con cierta incertidumbre"
                confidence >= 20 -> "Estimación poco fiable. Repite la medición."
                else             -> "Confianza insuficiente para determinar una dirección."
            }
        }
    }
}
