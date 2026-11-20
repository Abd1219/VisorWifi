package com.abdapps.visorwifi.speedtest.domain

/** Resultado terminal; las latencias son HTTP, nunca ICMP ni TCP. */
data class SpeedTestResult(
    val id: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val server: SpeedTestServer,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val latencyMinMs: Double?,
    val latencyAverageMs: Double?,
    val latencyMaxMs: Double?,
    val jitterMs: Double?,
    val successfulProbes: Int,
    val failedProbes: Int,
    val status: SpeedTestStatus,
    val error: String? = null,
    val cancellationReason: String? = null
)
