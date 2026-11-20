package com.abdapps.visorwifi.speedtest.connectivity

/** Cambios que una fase posterior podrá usar para decidir si interrumpe una medición. */
enum class ConnectivityChange {
    NETWORK_LOST,
    NETWORK_RESTORED,
    EFFECTIVE_TRANSPORT_CHANGED,
    UNDERLYING_NETWORK_CHANGED,
    VPN_STATE_CHANGED,
    INTERNET_AVAILABILITY_CHANGED,
    INTERNET_VALIDATION_CHANGED
}

object ConnectivityChangeDetector {
    fun compare(before: NetworkSnapshot, after: NetworkSnapshot): Set<ConnectivityChange> = buildSet {
        if (before.effectiveTransport != NetworkTransport.NONE && after.effectiveTransport == NetworkTransport.NONE) {
            add(ConnectivityChange.NETWORK_LOST)
        }
        if (before.effectiveTransport == NetworkTransport.NONE && after.effectiveTransport != NetworkTransport.NONE) {
            add(ConnectivityChange.NETWORK_RESTORED)
        }
        if (before.effectiveTransport != after.effectiveTransport) add(ConnectivityChange.EFFECTIVE_TRANSPORT_CHANGED)
        if (before.underlyingTransports != after.underlyingTransports ||
            (before.networkHandle != null && after.networkHandle != null && before.networkHandle != after.networkHandle)
        ) add(ConnectivityChange.UNDERLYING_NETWORK_CHANGED)
        if (before.isVpn != after.isVpn) add(ConnectivityChange.VPN_STATE_CHANGED)
        if (before.internetAvailable != after.internetAvailable) add(ConnectivityChange.INTERNET_AVAILABILITY_CHANGED)
        if (before.internetValidated != after.internetValidated) add(ConnectivityChange.INTERNET_VALIDATION_CHANGED)
    }
}
