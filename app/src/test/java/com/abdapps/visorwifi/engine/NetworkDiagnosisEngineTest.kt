package com.abdapps.visorwifi.engine

import com.abdapps.visorwifi.model.DiagnosisStatus
import com.abdapps.visorwifi.model.NetworkMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkDiagnosisEngineTest {

    @Test
    fun stableMetricsReturnHealthy() {
        val result = NetworkDiagnosisEngine.evaluateDiagnosis(
            metrics(
                lanAverage = 5f,
                lanMax = 10f,
                wanAverage = 35f,
                wanMax = 50f,
                rssi = -55,
                jitter = 4f,
                packetLoss = 0f
            )
        )

        assertEquals(DiagnosisStatus.HEALTHY, result.status)
        assertEquals("Red saludable", result.title)
    }

    @Test
    fun stableLanAndPoorWanReturnPossibleIspProblem() {
        val result = NetworkDiagnosisEngine.evaluateDiagnosis(
            metrics(
                lanAverage = 5f,
                lanMax = 10f,
                wanAverage = 140f,
                wanMax = 250f,
                rssi = -55,
                jitter = 10f,
                packetLoss = 8f
            )
        )

        assertEquals(DiagnosisStatus.PROBLEM, result.status)
        assertEquals("Posible problema del proveedor de internet", result.title)
    }

    @Test
    fun poorSignalAndLocalSpikesReturnWifiProblem() {
        val result = NetworkDiagnosisEngine.evaluateDiagnosis(
            metrics(
                lanAverage = 30f,
                lanMax = 120f,
                wanAverage = 60f,
                wanMax = 80f,
                rssi = -78,
                jitter = 12f,
                packetLoss = 0f
            )
        )

        assertEquals(DiagnosisStatus.PROBLEM, result.status)
        assertEquals("Interferencia o señal Wi-Fi débil", result.title)
    }

    @Test
    fun missingIcmpSamplesNeverReturnHealthy() {
        val result = NetworkDiagnosisEngine.evaluateDiagnosis(
            metrics(
                lanAverage = 0f,
                lanMax = 0f,
                wanAverage = 0f,
                wanMax = 0f,
                rssi = -55,
                jitter = 0f,
                packetLoss = 100f
            ),
            lanValidSamples = 0,
            wanValidSamples = 0
        )

        assertEquals(DiagnosisStatus.WARNING, result.status)
        assertEquals("Mediciones ICMP insuficientes", result.title)
    }

    private fun metrics(
        lanAverage: Float,
        lanMax: Float,
        wanAverage: Float,
        wanMax: Float,
        rssi: Int,
        jitter: Float,
        packetLoss: Float
    ) = NetworkMetrics(
        lanLatencyAvgMs = lanAverage,
        lanLatencyMaxMs = lanMax,
        wanLatencyAvgMs = wanAverage,
        wanLatencyMaxMs = wanMax,
        jitterMs = jitter,
        rssiDbm = rssi,
        phyRateMbps = 300,
        packetLossPercent = packetLoss
    )
}
