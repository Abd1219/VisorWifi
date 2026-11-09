package com.abdapps.visorwifi.model

/**
 * Estado cualitativo del diagnóstico de red.
 */
enum class DiagnosisStatus {
    HEALTHY,
    WARNING,
    PROBLEM,
    LOADING
}

/**
 * Representa el estado del diagnóstico de red en un momento dado.
 *
 * Esta data class define el contrato de datos entre la lógica de diagnóstico y el componente
 * visual [DiagnosisCard]. La UI no depende del algoritmo que construye este estado.
 *
 * @property status         Enum indicando el estado cualitativo (HEALTHY, WARNING, PROBLEM, LOADING).
 * @property title          Título del diagnóstico (ej. "Interferencia Wi-Fi probable").
 * @property confidence     Porcentaje de confianza del diagnóstico (0–100).
 * @property evidences      Lista de 2–3 evidencias detectadas (ej. "RSSI bajo: -72 dBm").
 * @property recommendation Acción sugerida al usuario (una sola frase concisa).
 */
data class DiagnosisState(
    val status: DiagnosisStatus,
    val title: String,
    val confidence: Int,
    val evidences: List<String>,
    val recommendation: String
)
