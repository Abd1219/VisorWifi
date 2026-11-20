package com.abdapps.visorwifi.speedtest.connectivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** JVM tests of the pure comparison contract. They do not emulate ConnectivityManager. */
class ConnectivityChangeDetectorTest {
    @Test fun wifiSnapshotCarriesWifiAndValidatedInternet() {
        val snapshot = network(NetworkTransport.WIFI, setOf(NetworkTransport.WIFI), validated = true)
        assertTrue(snapshot.hasWifi)
        assertFalse(snapshot.hasCellular)
        assertTrue(snapshot.internetAvailable)
        assertTrue(snapshot.internetValidated)
    }

    @Test fun cellularSnapshotCarriesCellularWithoutInventingRadioGeneration() {
        val snapshot = network(NetworkTransport.CELLULAR, setOf(NetworkTransport.CELLULAR))
        assertTrue(snapshot.hasCellular)
        assertFalse(snapshot.hasWifi)
        assertEquals(NetworkTransport.CELLULAR, snapshot.effectiveTransport)
    }

    @Test fun vpnOverWifiKeepsVpnEffectiveAndWifiUnderlying() {
        val snapshot = network(NetworkTransport.VPN, setOf(NetworkTransport.WIFI), vpn = true)
        assertEquals(NetworkTransport.VPN, snapshot.effectiveTransport)
        assertTrue(snapshot.isVpn)
        assertTrue(snapshot.hasWifi)
        assertFalse(snapshot.hasCellular)
    }

    @Test fun vpnOverCellularKeepsCellularUnderlying() {
        val snapshot = network(NetworkTransport.VPN, setOf(NetworkTransport.CELLULAR), vpn = true)
        assertTrue(snapshot.isVpn)
        assertTrue(snapshot.hasCellular)
        assertFalse(snapshot.hasWifi)
    }

    @Test fun noNetworkIsExplicitAndHasNoWifiValues() {
        val snapshot = NetworkSnapshot.noNetwork(100)
        assertEquals(NetworkTransport.NONE, snapshot.effectiveTransport)
        assertEquals(WifiInfoAvailability.NOT_ON_WIFI, snapshot.wifi.availability)
        assertFalse(snapshot.internetAvailable)
    }

    @Test fun unvalidatedInternetRemainsDistinctFromNoInternet() {
        val snapshot = network(NetworkTransport.WIFI, setOf(NetworkTransport.WIFI), validated = false)
        assertTrue(snapshot.internetAvailable)
        assertFalse(snapshot.internetValidated)
    }

    @Test fun lossOfConnectivityIsReported() {
        val changes = ConnectivityChangeDetector.compare(
            network(NetworkTransport.WIFI, setOf(NetworkTransport.WIFI)), NetworkSnapshot.noNetwork(2)
        )
        assertTrue(ConnectivityChange.NETWORK_LOST in changes)
        assertTrue(ConnectivityChange.EFFECTIVE_TRANSPORT_CHANGED in changes)
    }

    @Test fun changingWifiToCellularIsReported() {
        val changes = ConnectivityChangeDetector.compare(
            network(NetworkTransport.WIFI, setOf(NetworkTransport.WIFI)),
            network(NetworkTransport.CELLULAR, setOf(NetworkTransport.CELLULAR))
        )
        assertTrue(ConnectivityChange.EFFECTIVE_TRANSPORT_CHANGED in changes)
        assertTrue(ConnectivityChange.UNDERLYING_NETWORK_CHANGED in changes)
    }

    @Test fun vpnActivationIsReported() {
        val changes = ConnectivityChangeDetector.compare(
            network(NetworkTransport.WIFI, setOf(NetworkTransport.WIFI)),
            network(NetworkTransport.VPN, setOf(NetworkTransport.WIFI), vpn = true)
        )
        assertTrue(ConnectivityChange.VPN_STATE_CHANGED in changes)
        assertTrue(ConnectivityChange.EFFECTIVE_TRANSPORT_CHANGED in changes)
    }

    @Test fun vpnUnderlyingChangeIsReportedWhenItIsAvailable() {
        val changes = ConnectivityChangeDetector.compare(
            network(NetworkTransport.VPN, setOf(NetworkTransport.WIFI), vpn = true, handle = 10),
            network(NetworkTransport.VPN, setOf(NetworkTransport.CELLULAR), vpn = true, handle = 11)
        )
        assertTrue(ConnectivityChange.UNDERLYING_NETWORK_CHANGED in changes)
        assertFalse(ConnectivityChange.VPN_STATE_CHANGED in changes)
    }

    @Test fun missingWifiPermissionUsesExplicitUnavailableState() {
        val snapshot = network(
            NetworkTransport.WIFI,
            setOf(NetworkTransport.WIFI),
            wifi = WifiLinkInfo(WifiInfoAvailability.PERMISSION_OR_SYSTEM_UNAVAILABLE)
        )
        assertEquals(WifiInfoAvailability.PERMISSION_OR_SYSTEM_UNAVAILABLE, snapshot.wifi.availability)
        assertEquals(null, snapshot.wifi.ssid)
        assertEquals(null, snapshot.wifi.bssid)
    }

    @Test fun cancellingObservationUnregistersItsCallback() = runBlocking {
        val registration = FakeRegistration()
        val observation = SnapshotObservation(
            snapshot = { NetworkSnapshot.noNetwork(System.currentTimeMillis()) },
            registrationFactory = { registration }
        )
        observation.observe().first()
        assertEquals(1, registration.registerCalls)
        assertEquals(1, registration.unregisterCalls)
    }

    private fun network(
        effective: NetworkTransport,
        underlying: Set<NetworkTransport>,
        validated: Boolean = true,
        vpn: Boolean = false,
        handle: Long? = 1,
        wifi: WifiLinkInfo = WifiLinkInfo(WifiInfoAvailability.NOT_ON_WIFI)
    ) = NetworkSnapshot(
        timestampMillis = 1,
        networkHandle = handle,
        effectiveTransport = effective,
        underlyingTransports = underlying,
        hasWifi = NetworkTransport.WIFI in underlying,
        hasCellular = NetworkTransport.CELLULAR in underlying,
        isVpn = vpn,
        internetAvailable = effective != NetworkTransport.NONE,
        internetValidated = validated,
        isMetered = false,
        isCaptivePortal = false,
        wifi = wifi
    )

    private class FakeRegistration : SnapshotCallbackRegistration {
        var registerCalls = 0
        var unregisterCalls = 0
        override fun register(onChanged: () -> Unit) { registerCalls++ }
        override fun unregister() { unregisterCalls++ }
    }
}
