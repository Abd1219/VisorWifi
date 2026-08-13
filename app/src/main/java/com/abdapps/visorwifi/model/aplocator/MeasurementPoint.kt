package com.abdapps.visorwifi.model.aplocator

/**
 * Punto de medición posicional registrado en una sesión de AP Locator.
 *
 * Cada punto corresponde a un resultado de estimación desde una posición
 * física específica del técnico. La ubicación GPS es completamente opcional.
 *
 * @property estimatedAzimuthDeg Dirección estimada del AP desde este punto [0.0, 360.0).
 * @property rssi                RSSI registrado en este punto (dBm).
 * @property bestRssi            Mejor RSSI capturado durante el escaneo (dBm).
 * @property bssid               BSSID del AP objetivo medido.
 * @property frequencyMHz        Frecuencia del canal en MHz.
 * @property confidencePercent   Confianza de la estimación en este punto (0–100).
 * @property timestamp           Marca de tiempo Unix en ms del momento de la medición.
 * @property latitude            Latitud GPS opcional (null si no disponible).
 * @property longitude           Longitud GPS opcional (null si no disponible).
 */
data class MeasurementPoint(
    val estimatedAzimuthDeg: Double,
    val rssi: Int,
    val bestRssi: Int,
    val bssid: String,
    val frequencyMHz: Int,
    val confidencePercent: Int,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
)
