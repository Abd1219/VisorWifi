package com.abdapps.visorwifi.speedtest.config

import com.abdapps.visorwifi.speedtest.domain.SpeedTestServer

/**
 * Punto de inyección para un servidor LibreSpeed aprobado. No añade URLs públicas ni valores
 * predeterminados: hasta que el producto entregue una fuente concreta, la prueba real queda
 * correctamente bloqueada.
 */
fun interface SpeedTestServerSource {
    fun approvedServer(): SpeedTestServer?
}

object NoApprovedSpeedTestServerSource : SpeedTestServerSource {
    override fun approvedServer(): SpeedTestServer? = null
}
