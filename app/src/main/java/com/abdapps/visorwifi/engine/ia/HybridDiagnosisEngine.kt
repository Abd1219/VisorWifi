package com.abdapps.visorwifi.engine.ia

import android.util.Log
import com.abdapps.visorwifi.engine.NetworkDiagnosisEngine
import com.abdapps.visorwifi.model.DiagnosisState
import com.abdapps.visorwifi.model.DiagnosisStatus
import com.abdapps.visorwifi.model.LatencyPoint
import com.abdapps.visorwifi.model.NetworkMetrics
import java.util.Locale

/**
 * Orquestador e integrador híbrido de diagnóstico de red.
 *
 * Consolida la Fase 1 (reglas heurísticas instantáneas) y la Fase 2 (inferencia de IA
 * basada en ventanas de tiempo históricas). Resuelve conflictos y calcula la confianza final.
 */
object HybridDiagnosisEngine {

    private const val TAG = "HybridDiagnosisEngine"

    /**
     * Evalúa de forma híbrida las métricas inmediatas y el historial completo para producir un diagnóstico unificado.
     *
     * @param metrics     Métricas consolidadas a corto plazo (Fase 1).
     * @param history     Historial de telemetría de red para análisis de ventana (Fase 2).
     * @param isIaEnabled Flag para activar/desactivar la inferencia por IA.
     * @return El estado de diagnóstico unificado [DiagnosisState].
     */
    fun evaluate(
        metrics: NetworkMetrics,
        history: List<LatencyPoint>,
        isIaEnabled: Boolean
    ): DiagnosisState {
        // 1. Obtener diagnóstico base de la Fase 1 (Reglas Heurísticas)
        val ruleState = NetworkDiagnosisEngine.evaluateDiagnosis(metrics)

        if (!isIaEnabled) {
            Log.d(TAG, "Diagnóstico híbrido - IA desactivada. Retornando Fase 1.")
            return ruleState
        }

        // 2. Extraer características por ventana temporal (últimos 120-300 puntos)
        // Usamos los últimos 300 puntos de la sesión (ventana de ~300 segundos a 1Hz)
        val windowHistory = history.takeLast(300)
        val features = NetworkFeatures.fromHistory(windowHistory)

        // 3. Ejecutar inferencia de IA local (Fase 2)
        val iaResult = LightWeightClassifier.classify(features)

        // 4. Mapear salida de la IA a un DiagnosisState representativo
        val iaState = mapIaResultToState(iaResult, features)

        // 5. Integración y fusión inteligente de ambos diagnósticos
        return combineDiagnoses(ruleState, iaState, metrics, features, iaResult)
    }

    /**
     * Mapea el resultado del clasificador de IA local a una estructura de estado legible.
     */
    private fun mapIaResultToState(iaResult: IaDiagnosisResult, features: NetworkFeatures): DiagnosisState {
        val status = when (iaResult.predictedClass) {
            IaDiagnosisClass.HEALTHY -> DiagnosisStatus.HEALTHY
            IaDiagnosisClass.NETWORK_UNSTABLE -> DiagnosisStatus.WARNING
            IaDiagnosisClass.UNKNOWN -> DiagnosisStatus.WARNING
            IaDiagnosisClass.WIFI_INTERFERENCE, IaDiagnosisClass.WEAK_SIGNAL, IaDiagnosisClass.ISP_PROBLEM -> DiagnosisStatus.PROBLEM
        }

        val title = when (iaResult.predictedClass) {
            IaDiagnosisClass.HEALTHY -> "Red saludable (Análisis IA)"
            IaDiagnosisClass.WIFI_INTERFERENCE -> "Interferencia de canal Wi-Fi"
            IaDiagnosisClass.WEAK_SIGNAL -> "Señal Wi-Fi deficiente"
            IaDiagnosisClass.ISP_PROBLEM -> "Inestabilidad en el Proveedor de Internet (ISP)"
            IaDiagnosisClass.NETWORK_UNSTABLE -> "Calidad de red inestable"
            IaDiagnosisClass.UNKNOWN -> "Comportamiento de red inusual"
        }

        val evidences = iaResult.topFeatures.map { translateFeature(it, features) }

        val recommendation = when (iaResult.predictedClass) {
            IaDiagnosisClass.HEALTHY -> "Recomendación: Tu conexión está funcionando en condiciones óptimas en la sesión"
            IaDiagnosisClass.WIFI_INTERFERENCE -> "Recomendación: Cámbiate a una banda menos saturada (5 GHz / 6 GHz) o reubica el router"
            IaDiagnosisClass.WEAK_SIGNAL -> "Recomendación: Acércate al módem Wi-Fi o considera instalar un repetidor/malla"
            IaDiagnosisClass.ISP_PROBLEM -> "Recomendación: Reinicia el módem de tu proveedor o contacta a soporte técnico de tu ISP"
            IaDiagnosisClass.NETWORK_UNSTABLE -> "Recomendación: Limita las descargas pesadas en la red o reconecta el Wi-Fi de tu móvil"
            IaDiagnosisClass.UNKNOWN -> "Recomendación: Continúa monitoreando para recolectar más datos telemétricos"
        }

        return DiagnosisState(
            status = status,
            title = title,
            confidence = iaResult.confidenceScore,
            evidences = evidences,
            recommendation = recommendation
        )
    }

    /**
     * Combina y fusiona de forma híbrida los diagnósticos de Fase 1 y Fase 2.
     */
    private fun combineDiagnoses(
        ruleState: DiagnosisState,
        iaState: DiagnosisState,
        metrics: NetworkMetrics,
        features: NetworkFeatures,
        iaResult: IaDiagnosisResult
    ): DiagnosisState {
        // Traducir el título de la Fase 1 a una categoría equivalente de la IA para comparar
        val ruleClass = when {
            ruleState.title.contains("proveedor", ignoreCase = true) || ruleState.title.contains("isp", ignoreCase = true) -> IaDiagnosisClass.ISP_PROBLEM
            ruleState.title.contains("interferencia", ignoreCase = true) || ruleState.title.contains("débil", ignoreCase = true) -> {
                if (metrics.rssiDbm < -68) IaDiagnosisClass.WEAK_SIGNAL else IaDiagnosisClass.WIFI_INTERFERENCE
            }
            ruleState.title.contains("inestable", ignoreCase = true) || ruleState.title.contains("calidad", ignoreCase = true) -> IaDiagnosisClass.NETWORK_UNSTABLE
            ruleState.title.contains("saludable", ignoreCase = true) -> IaDiagnosisClass.HEALTHY
            else -> IaDiagnosisClass.UNKNOWN
        }

        val iaClass = iaResult.predictedClass
        val isMatch = (ruleClass == iaClass) || 
                      (ruleClass == IaDiagnosisClass.WEAK_SIGNAL && iaClass == IaDiagnosisClass.WIFI_INTERFERENCE) ||
                      (ruleClass == IaDiagnosisClass.WIFI_INTERFERENCE && iaClass == IaDiagnosisClass.WEAK_SIGNAL)

        val combinedStatus: DiagnosisStatus
        val combinedTitle: String
        val combinedConfidence: Int
        val combinedEvidences = mutableListOf<String>()
        val combinedRecommendation: String

        if (isMatch) {
            // --- COINCIDENCIA ---
            // Aumentar confianza (+10, tope de 100)
            combinedConfidence = minOf(100, maxOf(ruleState.confidence, iaState.confidence) + 10)
            combinedStatus = ruleState.status
            combinedTitle = ruleState.title // Mantener título principal de regla por familiaridad visual

            // Combinar evidencias (las físicas instantáneas y las estadísticas de la IA)
            combinedEvidences.addAll(ruleState.evidences.take(2))
            
            // Añadir las evidencias de la IA que no estén duplicadas
            val translatedIa = iaResult.topFeatures.map { translateFeature(it, features) }
            translatedIa.forEach { ev ->
                if (combinedEvidences.size < 4 && !combinedEvidences.any { it.substringBefore(":").trim() == ev.substringBefore(":").trim() }) {
                    combinedEvidences.add(ev)
                }
            }

            combinedRecommendation = ruleState.recommendation
            Log.d(TAG, "Fusión Híbrida - Coincidencia detectada ($ruleClass). Confianza aumentada a $combinedConfidence%")
        } else {
            // --- DISCREPANCIA ---
            // Reducir confianza (-20, piso de 40)
            combinedConfidence = maxOf(40, minOf(ruleState.confidence, iaState.confidence) - 20)

            // Ajustar severidad: si discrepan y uno es PROBLEM y otro es HEALTHY, atenuamos a WARNING
            combinedStatus = if (ruleState.status == DiagnosisStatus.PROBLEM || iaState.status == DiagnosisStatus.PROBLEM) {
                if (ruleState.status == DiagnosisStatus.HEALTHY || iaState.status == DiagnosisStatus.HEALTHY) {
                    DiagnosisStatus.WARNING
                } else {
                    DiagnosisStatus.PROBLEM
                }
            } else {
                ruleState.status
            }

            // Seleccionar título del diagnóstico más grave o el heurístico como ancla
            combinedTitle = if (combinedStatus == DiagnosisStatus.WARNING && ruleState.status == DiagnosisStatus.HEALTHY) {
                "Anomalía en tendencia detectada por IA"
            } else {
                ruleState.title
            }

            // Combinar evidencias y añadir nota de discrepancia menor
            combinedEvidences.addAll(ruleState.evidences.take(2))
            combinedEvidences.add("⚡ Tendencia IA: ${iaState.title}")
            combinedEvidences.add("⚠️ Fluctuación detectada en análisis de sesión")

            combinedRecommendation = if (ruleState.status == DiagnosisStatus.HEALTHY) {
                "Recomendación: ${iaState.recommendation.substringAfter("Recomendación:").trim()}"
            } else {
                ruleState.recommendation
            }
            Log.d(TAG, "Fusión Híbrida - Discrepancia ($ruleClass vs $iaClass). Confianza reducida a $combinedConfidence% y severidad ajustada.")
        }

        return DiagnosisState(
            status = combinedStatus,
            title = combinedTitle,
            confidence = combinedConfidence,
            evidences = combinedEvidences.distinct().take(4),
            recommendation = combinedRecommendation
        )
    }

    /**
     * Traduce el identificador técnico de la característica en texto de evidencia comprensible para el usuario.
     */
    private fun translateFeature(featureName: String, features: NetworkFeatures): String {
        return when (featureName) {
            "lanLatencyAvgMs" -> "Latencia LAN promedio: %.1f ms".format(Locale.US, features.lanLatencyAvgMs)
            "lanLatencyStdMs" -> "Variabilidad LAN (StdDev): %.1f ms".format(Locale.US, features.lanLatencyStdMs)
            "lanLatencyMaxMs" -> "Pico de latencia local: %.1f ms".format(Locale.US, features.lanLatencyMaxMs)
            "wanLatencyAvgMs" -> "Latencia WAN promedio: %.1f ms".format(Locale.US, features.wanLatencyAvgMs)
            "wanLatencyStdMs" -> "Variabilidad WAN (StdDev): %.1f ms".format(Locale.US, features.wanLatencyStdMs)
            "wanLatencyMaxMs" -> "Pico de latencia internet: %.1f ms".format(Locale.US, features.wanLatencyMaxMs)
            "jitterAvgMs" -> "Jitter promedio WAN: %.1f ms".format(Locale.US, features.jitterAvgMs)
            "jitterMaxMs" -> "Jitter máximo WAN: %.1f ms".format(Locale.US, features.jitterMaxMs)
            "rssiAvgDbm" -> "Señal promedio en sesión: %.1f dBm".format(Locale.US, features.rssiAvgDbm)
            "rssiMinDbm" -> "Señal mínima registrada: %.1f dBm".format(Locale.US, features.rssiMinDbm)
            "phyRateAvgMbps" -> "Tasa de enlace PHY: %.1f Mbps".format(Locale.US, features.phyRateAvgMbps)
            "packetLossPercent" -> "Pérdida de paquetes WAN: %.1f%%".format(Locale.US, features.packetLossPercent)
            "diffLanWanAvgMs" -> "Retraso WAN - LAN: %.1f ms".format(Locale.US, features.diffLanWanAvgMs)
            else -> featureName
        }
    }
}
