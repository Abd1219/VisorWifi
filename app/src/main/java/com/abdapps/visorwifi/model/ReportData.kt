package com.abdapps.visorwifi.model

import android.graphics.Bitmap

/**
 * Encapsula todos los datos requeridos para generar el reporte de red en formato PDF.
 *
 * ## Separación de responsabilidades (estado de periodo vs. estado en tiempo real)
 *
 * El estado del reporte ([periodStatus]) proviene **exclusivamente** del análisis estadístico
 * del historial de mediciones, calculado por [com.abdapps.visorwifi.analysis.PeriodAnalyzer]
 * y evaluado por [com.abdapps.visorwifi.analysis.PeriodStatusEvaluator].
 *
 * El estado de la UI en tiempo real ([DiagnosisStatus] del ViewModel) **no debe utilizarse**
 * para construir [ReportData]. Esa separación es intencional y garantiza que el PDF
 * represente el comportamiento completo del enlace durante el periodo analizado,
 * no una fotografía del último instante de medición.
 *
 * @property periodStatus      Estado del **periodo analizado** (OPTIMAL / DEGRADED / UNSTABLE / CRITICAL).
 *                             Derivado estadísticamente del historial. Fuente exclusiva del estado del PDF.
 * @property periodAnalysis    Métricas estadísticas completas del periodo. Alimenta las tablas del PDF.
 * @property diagnosisTitle    Título del diagnóstico de mayor relevancia en la sesión.
 * @property confidence        Nivel de confianza del diagnóstico automático (0–100).
 * @property evidences         Lista de evidencias detectadas durante la sesión (máx. 4).
 * @property recommendation    Recomendación técnica principal para el periodo.
 * @property executiveConclusion Conclusión ejecutiva redactada para el cliente/NOC.
 * @property lanLatencyAvgMs   Latencia LAN promedio del periodo (resumen rápido para cabecera).
 * @property wanLatencyAvgMs   Latencia WAN promedio del periodo (resumen rápido para cabecera).
 * @property wifiBand          Banda WiFi activa al momento de generar el reporte.
 * @property rssiDbm           RSSI instantáneo al generar el reporte (solo para contexto de sitio).
 * @property phyRateMbps       Velocidad PHY de enlace al generar el reporte.
 * @property latencyHistory    Historial completo de mediciones para renderizar las gráficas PDF.
 * @property networkEvents     Lista de eventos de red (roaming, cambio de SSID) del periodo.
 * @property technicianNotes   Observaciones de campo del técnico (opcional).
 * @property technicianSignature Bitmap de la firma digital del técnico (opcional).
 */
data class ReportData(
    // ─── Metadatos del documento ────────────────────────────────────────────
    val appName: String = "Visor WiFi",
    val reportType: String = "Reporte de Diagnóstico de Red",
    val dateTime: String,
    val technicianName: String,
    val siteName: String,

    // ─── Estado del PERIODO (fuente única de verdad para el PDF) ────────────
    // IMPORTANTE: Este campo NO debe provenir de DiagnosisStatus del ViewModel.
    // Debe ser el resultado de PeriodStatusEvaluator.evaluate(periodAnalysis).
    val periodStatus: PeriodReportStatus,
    val periodAnalysis: PeriodAnalysis,

    // ─── Diagnóstico de la sesión (información adicional, NO determina el estado) ─
    val diagnosisTitle: String,
    val confidence: Int,
    val evidences: List<String>,
    val recommendation: String,
    val executiveConclusion: String,

    // ─── Métricas de resumen rápido (derivadas de periodAnalysis) ───────────
    val lanLatencyAvgMs: Float,
    val wanLatencyAvgMs: Float,
    val wifiBand: String,
    val rssiDbm: Int,
    val phyRateMbps: Int,

    // ─── Datos de evidencia gráfica ─────────────────────────────────────────
    val latencyHistory: List<LatencyPoint>,
    val networkEvents: List<String>,

    // ─── Firma del técnico ──────────────────────────────────────────────────
    val technicianNotes: String? = null,
    val technicianSignature: Bitmap? = null
)
