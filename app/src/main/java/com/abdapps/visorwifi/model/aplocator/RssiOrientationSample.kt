package com.abdapps.visorwifi.model.aplocator

/**
 * Medición atómica de señal Wi-Fi e orientación del dispositivo capturada en un instante.
 *
 * @property azimuthDeg  Azimut del dispositivo en grados [0.0, 360.0) respecto al norte magnético.
 * @property rssi        Intensidad de señal recibida en dBm. Rango típico: -30 (excelente) a -90 (débil).
 * @property timestamp   Marca de tiempo Unix en milisegundos.
 * @property bssid       Dirección MAC del Access Point objetivo (identificador único del AP).
 * @property frequencyMHz Frecuencia del canal Wi-Fi en MHz (ej: 2437, 5220, 6135).
 */
data class RssiOrientationSample(
    val azimuthDeg: Double,
    val rssi: Int,
    val timestamp: Long,
    val bssid: String,
    val frequencyMHz: Int
)
