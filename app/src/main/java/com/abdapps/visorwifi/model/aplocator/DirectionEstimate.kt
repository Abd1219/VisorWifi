package com.abdapps.visorwifi.model.aplocator

/**
 * Resultado del algoritmo de estimación de dirección del AP.
 *
 * La dirección es una **estimación** basada en variaciones de RSSI durante
 * la rotación del dispositivo. No representa un ángulo físico de llegada real.
 *
 * @property azimuthDeg        Azimut estimado normalizado en [0.0, 360.0).
 * @property confidencePercent Confianza de la estimación entre 0 y 100.
 *                             0 = estimación inválida, 100 = máxima confianza alcanzable.
 * @property message           Descripción legible del estado de la estimación.
 * @property bestRssi          Mejor RSSI registrado durante el escaneo (dBm).
 * @property avgRssi           RSSI promedio de las muestras válidas (dBm).
 * @property warningCode       Código de advertencia opcional si la medición es problemática.
 */
data class DirectionEstimate(
    val azimuthDeg: Double,
    val confidencePercent: Int,
    val message: String,
    val bestRssi: Int,
    val avgRssi: Float,
    val warningCode: WarningCode = WarningCode.NONE
) {
    /**
     * Códigos de advertencia para condiciones que reducen la fiabilidad de la estimación.
     */
    enum class WarningCode {
        NONE,
        INSUFFICIENT_SAMPLES,   // Menos de 12 muestras capturadas
        FLAT_RSSI,              // Variación de señal insuficiente (rango < 5 dBm)
        UNSTABLE_RSSI,          // RSSI demasiado variable (varianza alta en zona de pico)
        INCOMPLETE_ROTATION,    // Cobertura angular insuficiente (< 180°)
        BSSID_CHANGED,          // El AP cambió durante la medición
        SENSOR_UNAVAILABLE,     // Sensor de orientación no disponible
        TOO_FEW_PEAK_SAMPLES    // La zona de pico tiene muy pocas muestras consistentes
    }

    val isValid: Boolean
        get() = confidencePercent > 0 && warningCode != WarningCode.BSSID_CHANGED
                && warningCode != WarningCode.SENSOR_UNAVAILABLE
}
