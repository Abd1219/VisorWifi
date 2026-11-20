package com.abdapps.visorwifi.speedtest.connectivity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.flow.Flow

/** Fuente Android de la ruta por defecto de la aplicación, independiente del monitor existente. */
interface ConnectivitySnapshotProvider {
    fun currentSnapshot(): NetworkSnapshot
    fun observe(): Flow<NetworkSnapshot>
}

/**
 * Adaptador de [ConnectivityManager]. No solicita escaneos Wi-Fi y registra exactamente un
 * callback por colector; [awaitClose] lo libera al cancelar la observación.
 */
class AndroidConnectivitySnapshotProvider(context: Context) : ConnectivitySnapshotProvider {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    override fun currentSnapshot(): NetworkSnapshot = snapshotFor(connectivityManager?.activeNetwork)

    override fun observe(): Flow<NetworkSnapshot> {
        val manager = connectivityManager
        if (manager == null) {
            return kotlinx.coroutines.flow.flowOf(NetworkSnapshot.noNetwork(System.currentTimeMillis()))
        }
        return SnapshotObservation(::currentSnapshot) {
            AndroidCallbackRegistration(manager)
        }.observe()
    }

    private class AndroidCallbackRegistration(
        private val manager: ConnectivityManager
    ) : SnapshotCallbackRegistration {
        private var callback: ConnectivityManager.NetworkCallback? = null

        override fun register(onChanged: () -> Unit) {
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onChanged()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onChanged()
                override fun onLost(network: Network) = onChanged()
            }
            manager.registerDefaultNetworkCallback(requireNotNull(callback))
        }

        override fun unregister() {
            callback?.let(manager::unregisterNetworkCallback)
            callback = null
        }
    }

    private fun snapshotFor(network: Network?): NetworkSnapshot {
        val now = System.currentTimeMillis()
        if (network == null || connectivityManager == null) return NetworkSnapshot.noNetwork(now)
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkSnapshot.noNetwork(now)
        val isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        // Android's public NetworkCapabilities API does not expose a trustworthy association
        // between a VPN's default network and a particular physical bearer. Do not guess from
        // getAllNetworks(): candidates could belong to another profile or route.
        val physical = physicalTransports(capabilities)
        val effective = when {
            isVpn -> NetworkTransport.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            else -> NetworkTransport.OTHER
        }
        val onWifi = NetworkTransport.WIFI in physical
        return NetworkSnapshot(
            timestampMillis = now,
            networkHandle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) network.networkHandle else null,
            effectiveTransport = effective,
            underlyingTransports = physical,
            hasWifi = onWifi,
            hasCellular = NetworkTransport.CELLULAR in physical,
            isVpn = isVpn,
            internetAvailable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            internetValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            isCaptivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            wifi = readWifiInfo(onWifi)
        )
    }

    private fun physicalTransports(capabilities: NetworkCapabilities): Set<NetworkTransport> = buildSet {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add(NetworkTransport.WIFI)
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(NetworkTransport.CELLULAR)
        if (isEmpty() && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add(NetworkTransport.OTHER)
    }

    @Suppress("DEPRECATION")
    private fun readWifiInfo(onWifi: Boolean): WifiLinkInfo {
        if (!onWifi) return WifiLinkInfo(WifiInfoAvailability.NOT_ON_WIFI)
        if (!hasLocationPermission()) return WifiLinkInfo(WifiInfoAvailability.PERMISSION_OR_SYSTEM_UNAVAILABLE)
        return try {
            val info = wifiManager?.connectionInfo ?: return WifiLinkInfo(WifiInfoAvailability.PERMISSION_OR_SYSTEM_UNAVAILABLE)
            val ssid = info.ssid?.removeSurrounding("\"").takeUnless { it.isNullOrBlank() || it == "<unknown ssid>" }
            val bssid = info.bssid.takeUnless { it.isNullOrBlank() || it == "02:00:00:00:00:00" }
            // -127 is Android's documented sentinel for an unavailable RSSI.
            val rssi = info.rssi.takeIf { it > -127 && it <= 0 }
            val frequency = info.frequency.takeIf { it > 0 }
            val speed = info.linkSpeed.takeIf { it >= 0 }
            WifiLinkInfo(WifiInfoAvailability.AVAILABLE, ssid, bssid, rssi, frequency, speed)
        } catch (_: SecurityException) {
            WifiLinkInfo(WifiInfoAvailability.PERMISSION_OR_SYSTEM_UNAVAILABLE)
        }
    }

    private fun hasLocationPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
