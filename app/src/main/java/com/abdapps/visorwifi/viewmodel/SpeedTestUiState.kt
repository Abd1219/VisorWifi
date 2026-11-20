package com.abdapps.visorwifi.viewmodel

import com.abdapps.visorwifi.speedtest.connectivity.ConnectivityChange
import com.abdapps.visorwifi.speedtest.connectivity.NetworkSnapshot
import com.abdapps.visorwifi.speedtest.domain.SpeedTestResult
import com.abdapps.visorwifi.speedtest.domain.SpeedTestSample
import com.abdapps.visorwifi.speedtest.domain.SpeedTestStatus

data class SpeedTestUiState(
    val status: SpeedTestStatus = SpeedTestStatus.IDLE,
    val currentSnapshot: NetworkSnapshot,
    val initialSnapshot: NetworkSnapshot? = null,
    val finalSnapshot: NetworkSnapshot? = null,
    val connectivityChanges: Set<ConnectivityChange> = emptySet(),
    val latestSample: SpeedTestSample? = null,
    val result: SpeedTestResult? = null,
    val message: String? = null,
    val isServerConfigured: Boolean = false
)

object SpeedTestPresentation {
    fun phaseLabel(status: SpeedTestStatus): String = when (status) {
        SpeedTestStatus.IDLE -> "Listo para medir"
        SpeedTestStatus.PREPARING -> "Preparando servidor..."
        SpeedTestStatus.PINGING -> "Midiendo latencia HTTP..."
        SpeedTestStatus.DOWNLOADING -> "Midiendo descarga..."
        SpeedTestStatus.UPLOADING -> "Midiendo subida..."
        SpeedTestStatus.FINALIZING -> "Finalizando resultados..."
        SpeedTestStatus.COMPLETED -> "Prueba completada."
        SpeedTestStatus.FAILED -> "No se pudo completar la prueba."
        SpeedTestStatus.CANCELLED -> "Prueba cancelada."
        SpeedTestStatus.NETWORK_CHANGED -> "Cambio de red detectado."
    }

    fun isRunning(status: SpeedTestStatus): Boolean = status in setOf(
        SpeedTestStatus.PREPARING,
        SpeedTestStatus.PINGING,
        SpeedTestStatus.DOWNLOADING,
        SpeedTestStatus.UPLOADING,
        SpeedTestStatus.FINALIZING
    )
}
