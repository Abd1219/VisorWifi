package com.abdapps.visorwifi.speedtest.domain

/** Estado observable de una ejecución individual de Speed Test. */
enum class SpeedTestStatus {
    IDLE, PREPARING, PINGING, DOWNLOADING, UPLOADING, FINALIZING,
    COMPLETED, CANCELLED, FAILED, NETWORK_CHANGED
}
