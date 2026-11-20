package com.abdapps.visorwifi.speedtest.domain

import kotlin.math.abs

/** Cálculos sin compensación de cabeceras: bytes de aplicación / tiempo monotónico. */
object SpeedTestMetrics {
    fun megabitsPerSecond(bytes: Long, elapsedNanos: Long): Double? {
        if (bytes < 0 || elapsedNanos <= 0) return null
        return bytes * 8.0 * 1_000_000_000.0 / elapsedNanos / 1_000_000.0
    }

    /** Jitter HTTP = media de |latencia[i] - latencia[i-1]|; requiere al menos dos éxitos. */
    fun jitterMillis(latenciesMs: List<Double>): Double? =
        if (latenciesMs.size < 2) null else latenciesMs.zipWithNext { a, b -> abs(b - a) }.average()
}
