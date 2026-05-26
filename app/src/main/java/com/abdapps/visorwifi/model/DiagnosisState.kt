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
 * Esta data class define el contrato de datos entre la capa de lógica (ViewModel / motor de IA)
 * y el componente visual [DiagnosisCard]. El diseño es intencionalmente agnóstico respecto a
 * cómo se genera el diagnóstico: puede provenir de reglas heurísticas simples, un modelo de ML
 * local o una API remota, sin modificar la UI.
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
