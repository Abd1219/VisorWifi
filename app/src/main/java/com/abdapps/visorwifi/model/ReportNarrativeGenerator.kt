package com.abdapps.visorwifi.model

/**
 * Redacción objetiva para el reporte. Interpreta resultados ya calculados sin
 * cambiar el estado, el score ni ninguna métrica de telemetría.
 */
object ReportNarrativeGenerator {

    fun statusContext(analysis: PeriodAnalysis, status: PeriodReportStatus, diagnosisTitle: String): String {
        if (isStableWithIsolatedSpikes(analysis, status)) {
            return "El servicio se mantuvo estable durante la mayor parte del monitoreo. " +
                "Se detectaron ${analysis.latencySpikeCount} picos puntuales de latencia que " +
                "afectaron la calificación general del periodo, sin evidenciar una degradación constante."
        }

        if (status != PeriodReportStatus.OPTIMAL && diagnosisTitle.contains("saludable", ignoreCase = true)) {
            return "El diagnóstico describe el comportamiento predominante de las mediciones. " +
                "El estado general incorpora todo el periodo y también los eventos que afectaron " +
                "su calificación."
        }

        return when (status) {
            PeriodReportStatus.OPTIMAL -> "Las métricas del periodo se mantuvieron dentro de los rangos esperados."
            PeriodReportStatus.DEGRADED -> "Se registraron condiciones que redujeron la calificación del periodo; " +
                "el detalle de métricas indica cuáles estuvieron presentes."
            PeriodReportStatus.UNSTABLE -> "Durante el periodo se registraron variaciones relevantes que requieren seguimiento."
            PeriodReportStatus.CRITICAL -> "Durante el periodo se registraron condiciones críticas que requieren atención prioritaria."
        }
    }

    fun executiveConclusion(analysis: PeriodAnalysis, status: PeriodReportStatus): String {
        val statements = mutableListOf<String>()
        when {
            isStableWithIsolatedSpikes(analysis, status) -> {
                statements += "La conexión presentó un comportamiento estable durante la mayor parte del periodo monitoreado."
                statements += "Se registraron ${analysis.latencySpikeCount} picos aislados de latencia que redujeron la calificación general, sin observar una degradación constante."
            }
            analysis.avgWanLatencyMs in 0f..<100f && analysis.packetLossPercent < 2f ->
                statements += "La conexión mantuvo valores promedio de latencia y pérdida ICMP dentro de rangos estables durante el periodo monitoreado."
            else -> statements += "La conexión presentó condiciones ${statusLabel(status)} durante el periodo monitoreado."
        }

        when {
            analysis.rssiAvg > -127f && analysis.rssiAvg >= -60f -> statements += "La señal WiFi se mantuvo fuerte en promedio."
            analysis.rssiAvg > -127f && analysis.rssiAvg >= -70f -> statements += "La señal WiFi se mantuvo en un nivel adecuado en promedio."
            analysis.rssiAvg > -127f -> statements += "La intensidad promedio de la señal WiFi fue de ${analysis.rssiAvg.toInt()} dBm."
        }

        when {
            analysis.packetLossPercent == 0f -> statements += "No se detectaron pérdidas de paquetes ICMP durante el periodo monitoreado."
            analysis.packetLossPercent > 0f -> statements += "Se detectó una pérdida ICMP de ${"%.1f".format(java.util.Locale.US, analysis.packetLossPercent)}% durante el periodo monitoreado."
        }

        return statements.joinToString(" ")
    }

    private fun isStableWithIsolatedSpikes(analysis: PeriodAnalysis, status: PeriodReportStatus): Boolean =
        status == PeriodReportStatus.DEGRADED &&
            analysis.avgLanLatencyMs in 0f..<50f &&
            analysis.avgWanLatencyMs in 0f..<100f &&
            analysis.packetLossPercent < 2f &&
            analysis.rssiMin >= -72f &&
            analysis.roamingEventsCount == 0 &&
            analysis.stabilityScore >= 60 &&
            analysis.latencySpikeCount in 2..4

    private fun statusLabel(status: PeriodReportStatus): String = when (status) {
        PeriodReportStatus.OPTIMAL -> "óptimas"
        PeriodReportStatus.DEGRADED -> "degradadas"
        PeriodReportStatus.UNSTABLE -> "inestables"
        PeriodReportStatus.CRITICAL -> "críticas"
    }
}
