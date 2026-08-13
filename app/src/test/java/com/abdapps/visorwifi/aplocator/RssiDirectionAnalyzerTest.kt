package com.abdapps.visorwifi.aplocator

import com.abdapps.visorwifi.engine.aplocator.ConfidenceCalculator
import com.abdapps.visorwifi.engine.aplocator.RssiDirectionAnalyzer
import com.abdapps.visorwifi.model.aplocator.DirectionEstimate
import com.abdapps.visorwifi.model.aplocator.RssiOrientationSample
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Tests unitarios para el motor de análisis de dirección AP Locator.
 *
 * Todos los tests son puros (sin Android SDK) y deterministas.
 * El BSSID de prueba es "AA:BB:CC:11:22:33" a menos que se indique otro.
 */
class RssiDirectionAnalyzerTest {

    private val TEST_BSSID = "AA:BB:CC:11:22:33"
    private val ALT_BSSID  = "AA:BB:CC:11:22:44"
    private val BASE_TIME  = 1_700_000_000_000L

    // ─── Helpers de construcción de muestras ────────────────────────────────

    /**
     * Crea muestras distribuidas uniformemente en 360° con un perfil RSSI gaussiano
     * centrado en [peakAzimuth] con valor máximo [peakRssi] y base [baseRssi].
     */
    private fun buildGaussianSamples(
        peakAzimuthDeg: Double,
        peakRssi: Int,
        baseRssi: Int,
        totalSamples: Int = 72,   // ~1 muestra cada 5°
        bssid: String = TEST_BSSID,
        frequencyMHz: Int = 5220
    ): List<RssiOrientationSample> {
        val spread = 40.0  // desviación estándar angular en grados
        return (0 until totalSamples).map { i ->
            val azimuth = (i.toDouble() / totalSamples) * 360.0
            // Distancia angular circular al pico
            val rawDiff = abs(azimuth - peakAzimuthDeg)
            val angularDiff = minOf(rawDiff, 360.0 - rawDiff)
            // Perfil gaussiano: RSSI cae desde peakRssi hasta baseRssi
            val rssiOffset = ((peakRssi - baseRssi) * Math.exp(-(angularDiff * angularDiff) / (2 * spread * spread))).toInt()
            val rssi = baseRssi + rssiOffset
            RssiOrientationSample(
                azimuthDeg = azimuth,
                rssi = rssi.coerceIn(-127, 0),
                timestamp = BASE_TIME + i * 100L,
                bssid = bssid,
                frequencyMHz = frequencyMHz
            )
        }
    }

    /**
     * Crea muestras con RSSI uniforme (sin dirección dominante).
     */
    private fun buildFlatSamples(
        rssi: Int = -65,
        totalSamples: Int = 72,
        bssid: String = TEST_BSSID
    ): List<RssiOrientationSample> {
        return (0 until totalSamples).map { i ->
            RssiOrientationSample(
                azimuthDeg = (i.toDouble() / totalSamples) * 360.0,
                rssi = rssi,
                timestamp = BASE_TIME + i * 100L,
                bssid = bssid,
                frequencyMHz = 2437
            )
        }
    }

    // ─── Test 1: Estimación básica de dirección ──────────────────────────────

    @Test
    fun testBasicDirectionEstimation_peakAt90_resultApprox90() {
        // GIVEN: Muestras con pico en 90°
        val samples = buildGaussianSamples(peakAzimuthDeg = 90.0, peakRssi = -45, baseRssi = -70)

        // WHEN
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        // THEN: dirección estimada debe estar dentro de ±20° de 90°
        assertTrue("Estimación debe ser válida", result.confidencePercent > 0)
        val diff = angularDifference(result.azimuthDeg, 90.0)
        assertTrue(
            "Dirección estimada ${result.azimuthDeg}° debe estar ≈90° (diff=$diff°)",
            diff <= 20.0
        )
    }

    @Test
    fun testBasicDirectionEstimation_peakAt270_resultApprox270() {
        val samples = buildGaussianSamples(peakAzimuthDeg = 270.0, peakRssi = -48, baseRssi = -72)
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        assertTrue(result.confidencePercent > 0)
        val diff = angularDifference(result.azimuthDeg, 270.0)
        assertTrue("Estimación debe estar ≈270° (diff=$diff°)", diff <= 20.0)
    }

    // ─── Test 2: Smoothing — un outlier no debe dominar el resultado ─────────

    @Test
    fun testSmoothing_singleOutlierShouldNotDominateDirection() {
        // GIVEN: Muestras con pico en 90°, más un único outlier en 270°
        val normalSamples = buildGaussianSamples(peakAzimuthDeg = 90.0, peakRssi = -48, baseRssi = -68)
        val outlierSample = RssiOrientationSample(
            azimuthDeg = 270.0,
            rssi = -30,         // Señal extremadamente fuerte en 270° (outlier)
            timestamp = BASE_TIME + 999_000L,
            bssid = TEST_BSSID,
            frequencyMHz = 5220
        )
        val samples = normalSamples + outlierSample

        // WHEN
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        // THEN: La dirección no debe desplazarse a 270° (el outlier fue filtrado)
        val diffFrom90  = angularDifference(result.azimuthDeg, 90.0)
        val diffFrom270 = angularDifference(result.azimuthDeg, 270.0)
        assertTrue(
            "El outlier en 270° no debe ser la dirección dominante (est=${result.azimuthDeg}°)",
            diffFrom90 < diffFrom270
        )
    }

    // ─── Test 3: Confianza baja para señal uniforme ───────────────────────────

    @Test
    fun testLowConfidenceForFlatRssi() {
        // GIVEN: Todas las muestras tienen el mismo RSSI → sin dirección dominante
        val samples = buildFlatSamples(rssi = -65, totalSamples = 72)

        // WHEN
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        // THEN: Confianza debe ser 0 y warningCode = FLAT_RSSI
        assertEquals("Confianza debe ser 0 con RSSI uniforme", 0, result.confidencePercent)
        assertEquals(DirectionEstimate.WarningCode.FLAT_RSSI, result.warningCode)
    }

    @Test
    fun testLowConfidenceForNearFlatRssi_variationBelow5dBm() {
        // GIVEN: Variación de solo 3 dBm (por debajo del umbral de 5 dBm)
        val samples = (0 until 72).map { i ->
            RssiOrientationSample(
                azimuthDeg = (i.toDouble() / 72) * 360.0,
                rssi = -65 + (i % 4),  // Oscila entre -65 y -62: rango = 3 dBm
                timestamp = BASE_TIME + i * 100L,
                bssid = TEST_BSSID,
                frequencyMHz = 5220
            )
        }

        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        assertEquals("Confianza debe ser 0 con variación < 5 dBm", 0, result.confidencePercent)
    }

    // ─── Test 4: Confianza alta para pico claro ───────────────────────────────

    @Test
    fun testHighConfidenceForClearPeak() {
        // GIVEN: Pico pronunciado de 25 dBm de rango, 72 muestras
        val samples = buildGaussianSamples(
            peakAzimuthDeg = 180.0,
            peakRssi = -40,
            baseRssi = -75,
            totalSamples = 72
        )

        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        assertTrue("Confianza debe ser > 60% para pico claro, fue ${result.confidencePercent}%",
            result.confidencePercent > 60)
    }

    // ─── Test 5: Detección de cambio de BSSID ────────────────────────────────

    @Test
    fun testBssidChangeDetection_returnsBssidChangedWarning() {
        // GIVEN: Muestras mezcladas de dos BSSIDs diferentes
        val samplesA = buildGaussianSamples(
            peakAzimuthDeg = 90.0, peakRssi = -48, baseRssi = -70,
            totalSamples = 40, bssid = TEST_BSSID
        )
        val samplesB = buildGaussianSamples(
            peakAzimuthDeg = 270.0, peakRssi = -48, baseRssi = -70,
            totalSamples = 32, bssid = ALT_BSSID
        )
        val mixedSamples = samplesA + samplesB

        // WHEN: El target BSSID es el principal, pero hay muestras del alternativo
        val result = RssiDirectionAnalyzer.analyze(mixedSamples, TEST_BSSID)

        // THEN: Debe detectar el cambio de BSSID
        assertEquals(
            "Debe detectar cambio de BSSID",
            DirectionEstimate.WarningCode.BSSID_CHANGED,
            result.warningCode
        )
        assertEquals(0, result.confidencePercent)
    }

    // ─── Test 6: Muestras insuficientes ──────────────────────────────────────

    @Test
    fun testInsufficientSamples_lessThan12_confidenceIsZero() {
        // GIVEN: Solo 8 muestras (menos del mínimo de 12)
        val samples = buildGaussianSamples(
            peakAzimuthDeg = 90.0, peakRssi = -45, baseRssi = -70,
            totalSamples = 8
        )

        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        assertEquals("Con < 12 muestras la confianza debe ser 0", 0, result.confidencePercent)
        assertEquals(DirectionEstimate.WarningCode.INSUFFICIENT_SAMPLES, result.warningCode)
    }

    @Test
    fun testInsufficientSamples_exactlyZero_confidenceIsZero() {
        val result = RssiDirectionAnalyzer.analyze(emptyList(), TEST_BSSID)
        assertEquals(0, result.confidencePercent)
        assertEquals(DirectionEstimate.WarningCode.INSUFFICIENT_SAMPLES, result.warningCode)
    }

    @Test
    fun testInsufficientSamples_exactly11_confidenceIsZero() {
        val samples = buildGaussianSamples(
            peakAzimuthDeg = 45.0, peakRssi = -45, baseRssi = -70, totalSamples = 11
        )
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)
        assertEquals(0, result.confidencePercent)
    }

    @Test
    fun testInsufficientSamples_exactly12_nonZeroConfidencePossible() {
        val samples = buildGaussianSamples(
            peakAzimuthDeg = 45.0, peakRssi = -40, baseRssi = -75, totalSamples = 12
        )
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)
        // Con exactamente 12 muestras puede tener confianza > 0 si hay un pico claro
        // Solo verificamos que el warningCode no sea INSUFFICIENT_SAMPLES
        assertNotEquals(DirectionEstimate.WarningCode.INSUFFICIENT_SAMPLES, result.warningCode)
    }

    // ─── Test 7: Factores individuales del ConfidenceCalculator ─────────────

    @Test
    fun testConfidenceCalculator_sampleScoreZeroBelow12() {
        assertEquals(0, ConfidenceCalculator.calculateSampleScore(0))
        assertEquals(0, ConfidenceCalculator.calculateSampleScore(11))
    }

    @Test
    fun testConfidenceCalculator_sampleScoreMaxAt36OrMore() {
        assertEquals(20, ConfidenceCalculator.calculateSampleScore(36))
        assertEquals(20, ConfidenceCalculator.calculateSampleScore(100))
    }

    @Test
    fun testConfidenceCalculator_rssiRangeZeroForNarrowRange() {
        val narrowRange = listOf(-65, -64, -63, -64, -65)
        assertEquals(0, ConfidenceCalculator.calculateRssiRangeScore(narrowRange))
    }

    @Test
    fun testConfidenceCalculator_rssiRangeMaxForWideRange() {
        // Rango de 25+ dBm → máximo
        val wideRange = listOf(-40, -65)  // rango = 25 dBm
        assertEquals(25, ConfidenceCalculator.calculateRssiRangeScore(wideRange))
    }

    @Test
    fun testConfidenceCalculator_rssiRangeCorrectScaleForMedium() {
        val mediumRange = listOf(-50, -65)  // rango = 15 dBm → 75% de 25
        val score = ConfidenceCalculator.calculateRssiRangeScore(mediumRange)
        assertTrue("Score de rango medio debe estar entre 10 y 24, fue $score", score in 10..24)
    }

    @Test
    fun testConfidenceCalculator_peakVarianceLowForConsistentValues() {
        val consistent = listOf(-48, -47, -48, -49, -48)
        val score = ConfidenceCalculator.calculatePeakVarianceScore(consistent)
        assertEquals("Varianza baja debe dar score máximo (20)", 20, score)
    }

    @Test
    fun testConfidenceCalculator_peakVarianceZeroForHighSpread() {
        val spread = listOf(-30, -70, -35, -68, -32)  // stdDev ~18
        val score = ConfidenceCalculator.calculatePeakVarianceScore(spread)
        assertEquals("Varianza alta debe dar score 0", 0, score)
    }

    @Test
    fun testConfidenceCalculator_angularCoverageZeroForInsufficientRotation() {
        val score = ConfidenceCalculator.calculateAngularCoverageScore(0.0, 5)
        assertEquals(0, score)
    }

    @Test
    fun testConfidenceCalculator_angularCoverageMaxFor360With72Samples() {
        val score = ConfidenceCalculator.calculateAngularCoverageScore(360.0, 72)
        assertEquals("360° con 72 muestras debe dar score máximo (20)", 20, score)
    }

    // ─── Test 8: Wrap-around 359° → 0° ──────────────────────────────────────

    @Test
    fun testWrapAround_peakNear0_correctEstimation() {
        // GIVEN: Pico distribuido en torno a 0° (muestras en 350°, 355°, 0°, 5°, 10°)
        val samples = buildGaussianSamples(peakAzimuthDeg = 0.0, peakRssi = -45, baseRssi = -72, totalSamples = 72)
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        val diff = angularDifference(result.azimuthDeg, 0.0)
        assertTrue(
            "Pico en 0° debe estimarse ≈0° o ≈360° (diff=$diff°, est=${result.azimuthDeg}°)",
            diff <= 25.0
        )
    }

    @Test
    fun testWrapAround_peakNear350_correctEstimation() {
        val samples = buildGaussianSamples(peakAzimuthDeg = 350.0, peakRssi = -45, baseRssi = -72, totalSamples = 72)
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        val diff = angularDifference(result.azimuthDeg, 350.0)
        assertTrue(
            "Pico en 350° debe estimarse ≈350° (diff=$diff°, est=${result.azimuthDeg}°)",
            diff <= 25.0
        )
    }

    // ─── Test 9: Datos en azimuts desordenados ────────────────────────────────

    @Test
    fun testUnorderedAzimuths_correctEstimation() {
        // GIVEN: Muestras con azimuts en orden aleatorio
        val ordered = buildGaussianSamples(peakAzimuthDeg = 135.0, peakRssi = -48, baseRssi = -72, totalSamples = 48)
        val shuffled = ordered.shuffled(java.util.Random(42))

        val result = RssiDirectionAnalyzer.analyze(shuffled, TEST_BSSID)

        assertTrue(result.confidencePercent > 0)
        val diff = angularDifference(result.azimuthDeg, 135.0)
        assertTrue("Datos desordenados no deben afectar la estimación (diff=$diff°)", diff <= 25.0)
    }

    // ─── Test 10: RSSI negativo — escala correcta ─────────────────────────────

    @Test
    fun testNegativeRssiScaleCorrect_strongerSignalAtPeak() {
        // Verificar que el algoritmo no invierte la escala RSSI
        val samples = buildGaussianSamples(peakAzimuthDeg = 45.0, peakRssi = -40, baseRssi = -80, totalSamples = 72)
        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        // -40 es más fuerte que -80, así que el pico debe estar en 45°
        assertTrue("Debe ser válido", result.confidencePercent > 0)
        assertTrue("bestRssi debe ser el valor más fuerte (menos negativo)",
            result.bestRssi >= -50)
    }

    // ─── Test 11: Cobertura angular incompleta ────────────────────────────────

    @Test
    fun testIncompleteAngularCoverage_reducesConfidence() {
        // GIVEN: Muestras solo en el rango 60°–120° (cobertura muy parcial)
        val samples = (0 until 30).map { i ->
            RssiOrientationSample(
                azimuthDeg = 60.0 + (i.toDouble() / 29) * 60.0,
                rssi = if (i in 10..20) -48 else -68,
                timestamp = BASE_TIME + i * 100L,
                bssid = TEST_BSSID,
                frequencyMHz = 5220
            )
        }

        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        // Con solo 60° de cobertura la confianza debe ser muy baja o 0
        assertTrue("Cobertura parcial debe producir confianza baja (fue ${result.confidencePercent}%)",
            result.confidencePercent < 40)
    }

    // ─── Test 12: Múltiples muestras en el mismo sector ──────────────────────

    @Test
    fun testManyDuplicateSectors_doNotDistortEstimation() {
        // GIVEN: 22 muestras distribuidas en el resto del círculo a t=0..2200ms, y 50 muestras en 90° a t=3000ms..5500ms
        val rest = buildFlatSamples(rssi = -72, totalSamples = 22).filter {
            val az = it.azimuthDeg
            az < 70 || az > 110
        }
        val dense = (0 until 50).map {
            RssiOrientationSample(
                azimuthDeg = 90.0 + (it % 3 - 1) * 2.0,  // entre 88° y 92°
                rssi = -55,
                timestamp = BASE_TIME + 3000L + it * 50L,
                bssid = TEST_BSSID,
                frequencyMHz = 5220
            )
        }
        val samples = rest + dense

        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)
        // El resultado solo puede ser evaluado si hay suficiente cobertura
        if (result.confidencePercent > 0) {
            val diff = angularDifference(result.azimuthDeg, 90.0)
            assertTrue("Zona densa no debe distorsionar >30° (diff=$diff°)", diff <= 30.0)
        }
    }

    // ─── Test 13: Outliers no mueven el pico significativamente ──────────────

    @Test
    fun testMultipleOutliers_doNotDominateEstimation() {
        val normalSamples = buildGaussianSamples(peakAzimuthDeg = 180.0, peakRssi = -48, baseRssi = -70, totalSamples = 60)
        val outliers = listOf(
            RssiOrientationSample(30.0, -20, BASE_TIME + 10_000L, TEST_BSSID, 5220),
            RssiOrientationSample(60.0, -22, BASE_TIME + 10_100L, TEST_BSSID, 5220),
            RssiOrientationSample(300.0, -18, BASE_TIME + 10_200L, TEST_BSSID, 5220)
        )
        val samples = normalSamples + outliers

        val result = RssiDirectionAnalyzer.analyze(samples, TEST_BSSID)

        if (result.confidencePercent > 0) {
            val diff = angularDifference(result.azimuthDeg, 180.0)
            assertTrue("Outliers no deben mover estimación >35° (diff=$diff°)", diff <= 35.0)
        }
    }

    // ─── Test 14: Angular binning — wrap-around de bins ──────────────────────

    @Test
    fun testAngularBinning_wrapAround359To0() {
        val samples = listOf(
            RssiOrientationSample(359.5, -50, BASE_TIME, TEST_BSSID, 5220),
            RssiOrientationSample(0.5,   -50, BASE_TIME + 100L, TEST_BSSID, 5220),
            RssiOrientationSample(358.0, -50, BASE_TIME + 200L, TEST_BSSID, 5220)
        )
        val bins = RssiDirectionAnalyzer.buildAngularBins(samples)

        // 359.5° → bin 35 (350°-359°)
        // 0.5°   → bin 0  (0°-9°)
        // 358.0° → bin 35
        assertEquals("359.5° debe estar en bin 35", 2, bins[35].size)
        assertEquals("0.5° debe estar en bin 0", 1, bins[0].size)
    }

    // ─── Test 15: Median filter — remove outlier ──────────────────────────────

    @Test
    fun testMedianFilter_removesExtremeOutlier() {
        val samples = listOf(
            RssiOrientationSample(80.0, -61, BASE_TIME, TEST_BSSID, 5220),
            RssiOrientationSample(85.0, -60, BASE_TIME + 100, TEST_BSSID, 5220),
            RssiOrientationSample(90.0, -30, BASE_TIME + 200, TEST_BSSID, 5220),  // outlier!
            RssiOrientationSample(95.0, -60, BASE_TIME + 300, TEST_BSSID, 5220),
            RssiOrientationSample(100.0, -59, BASE_TIME + 400, TEST_BSSID, 5220),
        )

        val filtered = RssiDirectionAnalyzer.applyMedianFilter(samples, windowSize = 5)
        // La muestra central (índice 2) debe ser la mediana del conjunto {-61,-60,-30,-60,-59}
        // Ordenado: [-61, -60, -60, -59, -30] → mediana = -60
        assertEquals("Outlier -30 debe ser reemplazado por mediana -60", -60, filtered[2].rssi)
    }

    // ─── Test 16: Moving average suaviza valores ──────────────────────────────

    @Test
    fun testMovingAverage_smoothesValues() {
        val samples = listOf(
            RssiOrientationSample(0.0,  -70, BASE_TIME, TEST_BSSID, 5220),
            RssiOrientationSample(10.0, -50, BASE_TIME + 100, TEST_BSSID, 5220),
            RssiOrientationSample(20.0, -70, BASE_TIME + 200, TEST_BSSID, 5220),
            RssiOrientationSample(30.0, -50, BASE_TIME + 300, TEST_BSSID, 5220),
            RssiOrientationSample(40.0, -70, BASE_TIME + 400, TEST_BSSID, 5220),
        )

        val smoothed = RssiDirectionAnalyzer.applyMovingAverage(samples, windowSize = 5)
        // Promedio de toda la ventana = (-70-50-70-50-70)/5 = -62
        assertEquals(-62, smoothed[2].rssi)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Diferencia angular circular en grados [0, 180]. */
    private fun angularDifference(a: Double, b: Double): Double {
        val diff = abs(((a - b) % 360.0 + 360.0) % 360.0)
        return if (diff > 180.0) 360.0 - diff else diff
    }
}
