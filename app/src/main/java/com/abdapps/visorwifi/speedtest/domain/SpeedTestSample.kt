package com.abdapps.visorwifi.speedtest.domain

/** Muestra de bytes observados realmente durante una transferencia HTTP. */
data class SpeedTestSample(
    val timestampMonotonic: Long,
    val phase: SpeedTestStatus,
    val bytesTransferred: Long,
    val throughputMbps: Double?,
    val error: String? = null
)
