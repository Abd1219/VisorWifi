package com.abdapps.visorwifi.speedtest.connectivity

/**
 * Transporte de la red efectiva por defecto de la aplicación. VPN no elimina
 * necesariamente los transportes físicos, que se conservan en [NetworkSnapshot.underlyingTransports].
 */
enum class NetworkTransport { WIFI, CELLULAR, VPN, OTHER, NONE }

/** Explica por qué los campos Wi-Fi pueden no estar disponibles, sin inventar valores. */
enum class WifiInfoAvailability {
    NOT_ON_WIFI,
    AVAILABLE,
    PERMISSION_OR_SYSTEM_UNAVAILABLE
}

/** Información del enlace Wi-Fi actual; sólo se rellena cuando Android la entrega. */
data class WifiLinkInfo(
    val availability: WifiInfoAvailability,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssiDbm: Int? = null,
    val frequencyMHz: Int? = null,
    val linkSpeedMbps: Int? = null
)

/**
 * Fotografía inmutable de la ruta por defecto de la app.
 *
 * [networkHandle] es un identificador opaco, sólo para comparar snapshots dentro del proceso;
 * nunca es un SSID/BSSID ni debe persistirse o mostrarse. En API < 29 queda en null.
 */
data class NetworkSnapshot(
    val timestampMillis: Long,
    val networkHandle: Long?,
    val effectiveTransport: NetworkTransport,
    val underlyingTransports: Set<NetworkTransport>,
    val hasWifi: Boolean,
    val hasCellular: Boolean,
    val isVpn: Boolean,
    val internetAvailable: Boolean,
    val internetValidated: Boolean,
    val isMetered: Boolean,
    val isCaptivePortal: Boolean?,
    val wifi: WifiLinkInfo
) {
    companion object {
        fun noNetwork(timestampMillis: Long) = NetworkSnapshot(
            timestampMillis = timestampMillis,
            networkHandle = null,
            effectiveTransport = NetworkTransport.NONE,
            underlyingTransports = emptySet(),
            hasWifi = false,
            hasCellular = false,
            isVpn = false,
            internetAvailable = false,
            internetValidated = false,
            isMetered = false,
            isCaptivePortal = null,
            wifi = WifiLinkInfo(WifiInfoAvailability.NOT_ON_WIFI)
        )
    }
}
