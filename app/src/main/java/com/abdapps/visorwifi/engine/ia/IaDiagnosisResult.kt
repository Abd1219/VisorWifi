package com.abdapps.visorwifi.engine.ia

/**
 * Enumerado que representa las posibles clases de diagnóstico identificadas por la IA.
 */
enum class IaDiagnosisClass {
    HEALTHY,
    WIFI_INTERFERENCE,
    WEAK_SIGNAL,
    ISP_PROBLEM,
    NETWORK_UNSTABLE,
    UNKNOWN
}

/**
 * Representa el resultado intermedio emitido por la inferencia local del modelo de IA.
 *
 * @property predictedClass Clase de diagnóstico predicha por el clasificador.
 * @property confidenceScore Porcentaje de confianza del modelo sobre su predicción (0–100).
 * @property topFeatures Lista de las métricas (2-3) que más influyeron en la predicción.
 */
data class IaDiagnosisResult(
    val predictedClass: IaDiagnosisClass,
    val confidenceScore: Int,
    val topFeatures: List<String>
)
