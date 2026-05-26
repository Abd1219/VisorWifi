package com.abdapps.visorwifi.model

/**
 * Representa las métricas calculadas y consolidadas a partir de la telemetría de red.
 *
 * Sirve de entrada para el motor de diagnóstico [NetworkDiagnosisEngine].
 *
 * @property lanLatencyAvgMs   Latencia promedio LAN (Acceso Local / Gateway) en ms.
 * @property lanLatencyMaxMs   Latencia máxima LAN medida en ms.
 * @property wanLatencyAvgMs   Latencia promedio WAN (Internet) en ms.
 * @property wanLatencyMaxMs   Latencia máxima WAN medida en ms.
 * @property jitterMs          Jitter de red externa medido en ms (variabilidad temporal).
 * @property rssiDbm           Intensidad de señal Wi-Fi recibida en dBm.
 * @property phyRateMbps       Velocidad de enlace física actual en Mbps (PHY TX/RX).
 * @property packetLossPercent Porcentaje de paquetes perdidos (timeouts) en WAN, o null si no disponible.
 */
data class NetworkMetrics(
    val lanLatencyAvgMs: Float,
    val lanLatencyMaxMs: Float,
    val wanLatencyAvgMs: Float,
    val wanLatencyMaxMs: Float,
    val jitterMs: Float,
    val rssiDbm: Int,
    val phyRateMbps: Int,
    val packetLossPercent: Float?
)
