package com.abdapps.visorwifi.model

/**
 * Estado cualitativo del **periodo analizado** en el reporte PDF.
 *
 * Este enum es deliberadamente independiente de [DiagnosisStatus], que describe el estado
 * puntual en tiempo real de la UI. [PeriodReportStatus] es la conclusión estadística de
 * toda la ventana de tiempo monitorizada y es el único valor que debe utilizarse para
 * determinar el estado del informe PDF.
 *
 * Escala de severidad (de menor a mayor):
 *  - [OPTIMAL]   → Red estable y con buen rendimiento durante el periodo.
 *  - [DEGRADED]  → Rendimiento reducido o intermitente; sin impacto crítico.
 *  - [UNSTABLE]  → Inestabilidad frecuente; impacto notable en la experiencia.
 *  - [CRITICAL]  → Fallos graves o continuos; la red no cumple requisitos mínimos.
 */
enum class PeriodReportStatus {
    OPTIMAL,
    DEGRADED,
    UNSTABLE,
    CRITICAL
}
