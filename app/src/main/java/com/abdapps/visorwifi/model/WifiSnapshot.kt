package com.abdapps.visorwifi.model

/**
 * Snapshot telemétrico instantáneo de la conexión Wi-Fi actual.
 *
 * Permite a los distintos módulos (Monitor, AP Locator) consultar
 * la información del adaptador Wi-Fi sin depender de servicios en primer plano.
 *
 * @property bssid          Dirección MAC del AP conectado (ej. "00:11:22:33:44:55").
 * @property ssid           Nombre de la red Wi-Fi (SSID).
 * @property rssi           Intensidad de señal recibida en dBm.
 * @property frequencyMHz   Frecuencia del canal en MHz.
 * @property linkSpeedMbps  Velocidad física de enlace en Mbps.
 * @property isConnected    True si el dispositivo está actualmente asociado a un AP.
 */
data class WifiSnapshot(
    val bssid: String?,
    val ssid: String?,
    val rssi: Int = -127,
    val frequencyMHz: Int = -1,
    val linkSpeedMbps: Int = -1,
    val isConnected: Boolean = false
)
