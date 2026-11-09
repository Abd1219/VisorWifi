package com.abdapps.visorwifi.model

/**
 * Representa un punto de medición de latencia y estado de red inalámbrica en un instante de tiempo.
 * Agrupa la información de latencia de red local (LAN) y externa (WAN), junto con parámetros
 * telemétricos detallados del enlace WiFi actual del dispositivo.
 *
 * @property timestamp Marca de tiempo en milisegundos desde la época Unix en que se registró la medición.
 * @property lanLatency Tiempo ICMP en milisegundos hacia la puerta de enlace local. Retorna -1f si no hay respuesta.
 * @property wanLatency Tiempo ICMP en milisegundos hacia 8.8.8.8. Retorna -1f si no hay respuesta.
 * @property lanTcpLatency Tiempo de conexión TCP complementario hacia el gateway; se conserva separado de ICMP.
 * @property lanTcpTarget Último destino TCP probado para LAN (IP:puerto), incluso si falló.
 * @property wanTcpLatency Tiempo de conexión TCP complementario hacia Internet; se conserva separado de ICMP.
 * @property wanTcpTarget Último destino TCP probado para WAN (IP:puerto), incluso si falló.
 * @property ssid Nombre identificador de la red WiFi activa (Service Set Identifier). Nulo si no está conectado o no se tiene permiso de ubicación.
 * @property bssid Dirección MAC del punto de acceso inalámbrico actual (Basic Service Set Identifier). Útil para rastrear roaming.
 * @property rssi Indicador de fuerza de la señal recibida en dBm (Received Signal Strength Indicator). Rango típico: -30 (excelente) a -90 (muy débil). Por defecto -127.
 * @property frequency Frecuencia del canal de comunicación actual en MHz. Permite clasificar la banda (2.4 GHz, 5 GHz, 6 GHz). Por defecto -1.
 * @property linkSpeed Velocidad de enlace teórica agregada en Mbps (velocidad PHY genérica). Por defecto -1.
 * @property txLinkSpeed Velocidad teórica de transmisión en Mbps (disponible en API 29+). Por defecto -1.
 * @property rxLinkSpeed Velocidad teórica de recepción en Mbps (disponible en API 29+). Por defecto -1.
 */
data class LatencyPoint(
    val timestamp: Long,
    val lanLatency: Float,      // Latencia ICMP al gateway en ms (-1f si no responde)
    val wanLatency: Float,      // Latencia ICMP a 8.8.8.8 en ms (-1f si no responde)
    val lanTcpLatency: Float = -1f,
    val lanTcpTarget: String? = null,
    val wanTcpLatency: Float = -1f,
    val wanTcpTarget: String? = null,
    val ssid: String? = null,   // Nombre de la red WiFi
    val bssid: String? = null,  // Dirección MAC del Access Point actual (BSSID)
    val rssi: Int = -127,       // Intensidad de señal en dBm (ej: -55 dBm)
    val frequency: Int = -1,    // Frecuencia en MHz (ej: 2437 = 2.4GHz, 5220 = 5GHz, 6GHz > 5945)
    val linkSpeed: Int = -1,    // Velocidad PHY de enlace en Mbps (legacy)
    val txLinkSpeed: Int = -1,  // Velocidad PHY de Transmisión en Mbps (API 29+)
    val rxLinkSpeed: Int = -1   // Velocidad PHY de Recepción en Mbps (API 29+)
)
