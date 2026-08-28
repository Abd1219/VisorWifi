package com.abdapps.visorwifi.provider

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.abdapps.visorwifi.model.WifiSnapshot
import com.abdapps.visorwifi.model.aplocator.ApInfo

/**
 * Proveedor reutilizable de información del adaptador Wi-Fi.
 *
 * Responsabilidad única: Consultar el estado instantáneo de la conexión Wi-Fi
 * y los escaneos de redes visibles directamente desde el SDK de Android, sin
 * depender de servicios en primer plano (Foreground Service) ni de flujos de monitoreo.
 */
object WifiInfoProvider {

    /**
     * Obtiene una captura (snapshot) instantánea del estado del enlace Wi-Fi actual.
     *
     * @param context Contexto de la aplicación.
     * @return [WifiSnapshot] con la información del AP conectado o `null` si no está disponible.
     */
    @Suppress("DEPRECATION")
    fun getCurrentWifiSnapshot(context: Context): WifiSnapshot? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info: WifiInfo = wm?.connectionInfo ?: return null

            val bssid = info.bssid
            val rawSsid = info.ssid
            val cleanSsid = rawSsid?.removeSurrounding("\"")
            val isValidSsid = !cleanSsid.isNullOrBlank() && cleanSsid != "<unknown ssid>"
            val isValidBssid = !bssid.isNullOrBlank() && bssid != "02:00:00:00:00:00"

            val isConnected = isValidBssid && (isValidSsid || info.networkId != -1)

            WifiSnapshot(
                bssid = if (isValidBssid) bssid else null,
                ssid = if (isValidSsid) cleanSsid else null,
                rssi = info.rssi,
                frequencyMHz = info.frequency,
                linkSpeedMbps = info.linkSpeed,
                isConnected = isConnected
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtiene la lista de Access Points visibles cercanos a través del resultado de escaneo del sistema.
     *
     * @param context Contexto de la aplicación.
     * @return Lista de objetos [ApInfo] desacoplados.
     */
    @Suppress("DEPRECATION")
    fun getAvailableAps(context: Context): List<ApInfo> {
        val list = mutableListOf<ApInfo>()

        // 1. Incluir primero el AP actualmente conectado si está disponible
        val currentSnapshot = getCurrentWifiSnapshot(context)
        if (currentSnapshot != null && currentSnapshot.isConnected && currentSnapshot.bssid != null) {
            list.add(
                ApInfo(
                    bssid = currentSnapshot.bssid,
                    ssid = currentSnapshot.ssid ?: "Wi-Fi Conectado",
                    rssi = currentSnapshot.rssi,
                    frequencyMHz = currentSnapshot.frequencyMHz,
                    isConnected = true
                )
            )
        }

        // 2. Consultar ScanResults adicionales si existen
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val scanResults: List<ScanResult>? = wm?.scanResults
            if (!scanResults.isNullOrEmpty()) {
                scanResults.forEach { result ->
                    val bssid = result.BSSID
                    if (!bssid.isNullOrBlank() && bssid != currentSnapshot?.bssid) {
                        val ssidName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            result.wifiSsid?.toString()?.removeSurrounding("\"") ?: result.SSID
                        } else {
                            result.SSID
                        }
                        if (!ssidName.isNullOrBlank()) {
                            list.add(
                                ApInfo(
                                    bssid = bssid,
                                    ssid = ssidName,
                                    rssi = result.level,
                                    frequencyMHz = result.frequency,
                                    isConnected = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
            // Retorna lo acumulado hasta el momento
        }

        return list.distinctBy { it.bssid }
    }
}
