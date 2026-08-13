package com.abdapps.visorwifi.model.aplocator

/**
 * Sesión completa de localización de AP.
 *
 * Agrupa todos los puntos de medición realizados durante una sesión de trabajo,
 * identificando el AP objetivo por BSSID. La persistencia entre reinicios de la
 * aplicación no está implementada en la Fase 1.
 *
 * @property bssid            BSSID del AP objetivo (identificador principal).
 * @property ssid             SSID de la red (solo referencia — no usar como identificador único).
 * @property sessionName      Nombre descriptivo de la sesión (ej. "Oficina Piso 2").
 * @property measurementPoints Lista de puntos de medición registrados durante la sesión.
 * @property startTimestamp   Timestamp de inicio de la sesión.
 */
data class ApLocatorSession(
    val bssid: String,
    val ssid: String,
    val sessionName: String = "",
    val measurementPoints: List<MeasurementPoint> = emptyList(),
    val startTimestamp: Long = System.currentTimeMillis()
) {
    val pointCount: Int get() = measurementPoints.size

    val bestRssiOverall: Int?
        get() = measurementPoints.maxOfOrNull { it.bestRssi }

    val avgConfidence: Int
        get() = if (measurementPoints.isEmpty()) 0
                else measurementPoints.sumOf { it.confidencePercent } / measurementPoints.size

    val avgAzimuth: Double?
        get() {
            if (measurementPoints.isEmpty()) return null
            // Promedio circular para evitar artefactos en el wrap-around
            val sinSum = measurementPoints.sumOf { Math.sin(Math.toRadians(it.estimatedAzimuthDeg)) }
            val cosSum = measurementPoints.sumOf { Math.cos(Math.toRadians(it.estimatedAzimuthDeg)) }
            return (Math.toDegrees(Math.atan2(sinSum, cosSum)) + 360.0) % 360.0
        }
}
