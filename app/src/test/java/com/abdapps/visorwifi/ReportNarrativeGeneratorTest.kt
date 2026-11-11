package com.abdapps.visorwifi

import com.abdapps.visorwifi.model.PeriodAnalysis
import com.abdapps.visorwifi.model.PeriodReportStatus
import com.abdapps.visorwifi.model.ReportNarrativeGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportNarrativeGeneratorTest {

    @Test
    fun degradedPeriodWithIsolatedSpikes_explainsTheDifferenceFromPredominantBehavior() {
        val analysis = stableAnalysis(latencySpikeCount = 2)

        val context = ReportNarrativeGenerator.statusContext(
            analysis,
            PeriodReportStatus.DEGRADED,
            "Red saludable"
        )
        val conclusion = ReportNarrativeGenerator.executiveConclusion(analysis, PeriodReportStatus.DEGRADED)

        assertTrue(context.contains("picos puntuales"))
        assertTrue(context.contains("sin evidenciar una degradación constante"))
        assertTrue(conclusion.contains("No se detectaron pérdidas de paquetes ICMP"))
    }

    @Test
    fun conclusionDoesNotClaimNoLossWhenLossExists() {
        val conclusion = ReportNarrativeGenerator.executiveConclusion(
            stableAnalysis(packetLossPercent = 2.5f),
            PeriodReportStatus.DEGRADED
        )

        assertTrue(conclusion.contains("pérdida ICMP de 2.5%"))
    }

    private fun stableAnalysis(
        latencySpikeCount: Int = 0,
        packetLossPercent: Float = 0f
    ) = PeriodAnalysis(
        periodStartMs = 0L, periodEndMs = 60_000L, sampleCount = 60,
        avgLanLatencyMs = 5f, maxLanLatencyMs = 9f, lanSpikeCount = 0,
        avgWanLatencyMs = 25f, maxWanLatencyMs = 240f, minWanLatencyMs = 15f,
        latencySpikeCount = latencySpikeCount, packetLossPercent = packetLossPercent,
        avgJitterMs = 3f, maxJitterMs = 10f,
        rssiAvg = -55f, rssiMin = -60f, rssiMax = -48f, rssiVariance = 2f,
        roamingEventsCount = 0, ssidChangeCount = 0, stabilityScore = 75
    )
}
