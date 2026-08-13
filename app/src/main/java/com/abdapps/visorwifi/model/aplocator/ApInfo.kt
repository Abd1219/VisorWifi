package com.abdapps.visorwifi.model.aplocator

/**
 * Información técnica de un Access Point Wi-Fi detectado.
 *
 * El fabricante se determina mediante un lookup OUI básico embebido.
 * No se realiza ninguna consulta a servidores externos.
 *
 * @property bssid        Dirección MAC del AP (identificador único de hardware).
 * @property ssid         Nombre de la red (SSID). Puede ser idéntico en múltiples APs del mismo sistema.
 * @property rssi         RSSI medido en dBm.
 * @property frequencyMHz Frecuencia del canal en MHz.
 * @property isConnected  True si el dispositivo está actualmente conectado a este AP.
 */
data class ApInfo(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequencyMHz: Int,
    val isConnected: Boolean = false
) {
    /**
     * Nombre comercial de la banda de frecuencia.
     */
    val bandLabel: String
        get() = when {
            frequencyMHz in 2400..2500  -> "2.4 GHz"
            frequencyMHz in 5150..5850  -> "5 GHz"
            frequencyMHz >= 5945        -> "6 GHz"
            else                        -> "Desconocida"
        }

    /**
     * Número de canal Wi-Fi calculado a partir de la frecuencia.
     * Soporta canales de 2.4 GHz, 5 GHz y 6 GHz (Wi-Fi 6E).
     */
    val channel: Int
        get() = when {
            frequencyMHz in 2412..2472 -> (frequencyMHz - 2407) / 5
            frequencyMHz == 2484       -> 14
            frequencyMHz in 5160..5885 -> (frequencyMHz - 5000) / 5
            frequencyMHz in 5955..7115 -> (frequencyMHz - 5950) / 5
            else                       -> -1
        }

    /**
     * Fabricante aproximado del AP basado en los primeros 3 octetos del BSSID (OUI).
     * Lookup OUI básico con fabricantes de AP corporativos comunes.
     */
    val manufacturer: String
        get() {
            val oui = bssid.uppercase().take(8) // "XX:XX:XX"
            return OUI_TABLE[oui] ?: inferManufacturerFromOui(bssid)
        }

    private fun inferManufacturerFromOui(mac: String): String {
        val prefix = mac.uppercase().take(2)
        return when (prefix) {
            "00", "02" -> "Sin información"
            else       -> "Sin información"
        }
    }

    companion object {
        // OUI parcial para fabricantes de equipos de red más comunes.
        // No es una base de datos completa — solo orientación básica para técnicos.
        private val OUI_TABLE = mapOf(
            "00:18:0A" to "Cisco",
            "00:1E:49" to "Cisco",
            "00:25:45" to "Cisco",
            "34:DB:FD" to "Cisco",
            "F8:72:EA" to "Cisco",
            "00:0B:85" to "Cisco (Aironet)",
            "00:1A:A2" to "Cisco (Aironet)",
            "B8:38:61" to "Cisco Meraki",
            "E8:3A:12" to "Cisco Meraki",
            "00:18:74" to "Ubiquiti",
            "04:18:D6" to "Ubiquiti",
            "24:A4:3C" to "Ubiquiti",
            "44:D9:E7" to "Ubiquiti",
            "68:72:51" to "Ubiquiti",
            "80:2A:A8" to "Ubiquiti",
            "DC:9F:DB" to "Ubiquiti",
            "F4:92:BF" to "Ubiquiti",
            "FC:EC:DA" to "Ubiquiti",
            "00:17:88" to "Philips Hue / Ubiquiti",
            "00:0C:E7" to "Huawei",
            "28:31:52" to "Huawei",
            "48:FD:8E" to "Huawei",
            "54:89:98" to "Huawei",
            "6C:40:08" to "Huawei",
            "AC:85:3D" to "Huawei",
            "D0:7A:B5" to "Huawei",
            "00:24:2C" to "Ruckus",
            "18:9C:5D" to "Ruckus",
            "2C:5D:93" to "Ruckus",
            "D4:68:4D" to "Ruckus",
            "00:1A:1E" to "Aruba (HPE)",
            "24:DE:C6" to "Aruba (HPE)",
            "40:E3:D6" to "Aruba (HPE)",
            "6C:F3:7F" to "Aruba (HPE)",
            "9C:1C:12" to "Aruba (HPE)",
            "B4:5D:50" to "Aruba (HPE)",
            "00:0F:7D" to "TP-Link",
            "50:C7:BF" to "TP-Link",
            "54:AF:97" to "TP-Link",
            "A0:F3:C1" to "TP-Link",
            "B0:BE:76" to "TP-Link",
            "C0:06:C3" to "TP-Link",
            "00:50:BA" to "NETGEAR",
            "20:4E:7F" to "NETGEAR",
            "A0:40:A0" to "NETGEAR",
            "C0:3F:0E" to "NETGEAR",
            "E0:46:9A" to "NETGEAR",
            "00:26:B9" to "Dell",
            "18:66:DA" to "D-Link",
            "1C:7E:E5" to "D-Link",
            "28:10:7B" to "D-Link",
            "00:11:5F" to "Zyxel",
            "00:A0:C5" to "Zyxel",
            "1C:74:0D" to "Zyxel",
            "00:04:ED" to "Fortinet",
            "90:6C:AC" to "Fortinet",
            "A8:D3:F7" to "Fortinet",
            "00:0C:41" to "Extreme Networks",
            "00:04:96" to "Extreme Networks",
            "00:11:88" to "Motorola Solutions",
            "00:15:70" to "Motorola Solutions"
        )
    }
}
