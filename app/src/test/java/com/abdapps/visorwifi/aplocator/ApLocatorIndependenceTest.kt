package com.abdapps.visorwifi.aplocator

import com.abdapps.visorwifi.engine.aplocator.RssiDirectionAnalyzer
import com.abdapps.visorwifi.model.WifiSnapshot
import com.abdapps.visorwifi.model.aplocator.ApInfo
import com.abdapps.visorwifi.model.aplocator.RssiOrientationSample
import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas unitarias para verificar el modelo y desacoplamiento de AP Locator.
 *
 * Confirma que las estructuras de datos de AP Locator y WifiSnapshot
 * funcionan de forma independiente sin dependencias de Android SDK ni NetworkMonitorService.
 */
class ApLocatorIndependenceTest {

    @Test
    fun testWifiSnapshotModel_createsValidInstance() {
        val snapshot = WifiSnapshot(
            bssid = "AA:BB:CC:DD:EE:FF",
            ssid = "TestNet",
            rssi = -60,
            frequencyMHz = 5220,
            linkSpeedMbps = 433,
            isConnected = true
        )

        assertEquals("AA:BB:CC:DD:EE:FF", snapshot.bssid)
        assertEquals("TestNet", snapshot.ssid)
        assertEquals(-60, snapshot.rssi)
        assertEquals(5220, snapshot.frequencyMHz)
        assertTrue(snapshot.isConnected)
    }

    @Test
    fun testApInfoCreationFromSnapshot_withoutServiceDependency() {
        val snapshot = WifiSnapshot(
            bssid = "00:18:74:11:22:33",
            ssid = "Corporate_WiFi",
            rssi = -55,
            frequencyMHz = 5180,
            isConnected = true
        )

        val apInfo = ApInfo(
            bssid = snapshot.bssid!!,
            ssid = snapshot.ssid!!,
            rssi = snapshot.rssi,
            frequencyMHz = snapshot.frequencyMHz,
            isConnected = snapshot.isConnected
        )

        assertEquals("00:18:74:11:22:33", apInfo.bssid)
        assertEquals("Ubiquiti", apInfo.manufacturer)
        assertEquals("5 GHz", apInfo.bandLabel)
        assertEquals(36, apInfo.channel)
    }

    @Test
    fun testRssiSampleFromSnapshot_canBeAnalyzedDirectly() {
        val bssid = "11:22:33:44:55:66"
        val samples = (0 until 36).map { i ->
            val az = i * 10.0
            val rssi = if (i in 8..10) -45 else -75
            RssiOrientationSample(
                azimuthDeg = az,
                rssi = rssi,
                timestamp = System.currentTimeMillis() + i * 100L,
                bssid = bssid,
                frequencyMHz = 5220
            )
        }

        val estimate = RssiDirectionAnalyzer.analyze(samples, bssid)
        assertNotNull(estimate)
        assertTrue(estimate.confidencePercent > 0)
    }
}
