package com.abdapps.visorwifi.engine.ia

import android.util.Log

/**
 * Clasificador ligero y determinista por scoring (ponderación heurística).
 *
 * Analiza un conjunto estructurado de características estadísticas ([NetworkFeatures])
 * y produce una predicción categórica ([IaDiagnosisClass]) con un nivel de confianza y
 * los factores o características más influyentes.
 */
object LightWeightClassifier {

    private const val TAG = "LightWeightClassifier"

    /**
     * Evalúa las características y realiza la clasificación en base a reglas de scoring ponderadas.
     *
     * @param features Atributos consolidados de la red en la ventana temporal.
     * @return El resultado del diagnóstico de la IA [IaDiagnosisResult].
     */
    fun classify(features: NetworkFeatures): IaDiagnosisResult {
        val scores = mutableMapOf<IaDiagnosisClass, Float>()
        
        // Inicializar todas las clases candidatas con puntuación base 0
        IaDiagnosisClass.values().forEach { scores[it] = 0f }

        // --- 1. Reglas Relacionadas con RSSI y Enlace Físico (Señal) ---
        val rssi = features.rssiAvgDbm
        val minRssi = features.rssiMinDbm
        val phyRate = features.phyRateAvgMbps

        if (rssi < -75f) {
            scores[IaDiagnosisClass.WEAK_SIGNAL] = scores[IaDiagnosisClass.WEAK_SIGNAL]!! + 45f
            scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 15f
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 40f
        } else if (rssi < -68f) {
            scores[IaDiagnosisClass.WEAK_SIGNAL] = scores[IaDiagnosisClass.WEAK_SIGNAL]!! + 20f
            scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 10f
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 10f
        } else if (rssi >= -65f && rssi > -120f) {
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! + 25f
            scores[IaDiagnosisClass.WEAK_SIGNAL] = scores[IaDiagnosisClass.WEAK_SIGNAL]!! - 35f
        }

        if (minRssi < -82f && minRssi > -120f) {
            scores[IaDiagnosisClass.WEAK_SIGNAL] = scores[IaDiagnosisClass.WEAK_SIGNAL]!! + 15f
        }

        if (phyRate > 0f && phyRate < 45f) {
            scores[IaDiagnosisClass.WEAK_SIGNAL] = scores[IaDiagnosisClass.WEAK_SIGNAL]!! + 20f
            scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 15f
        } else if (phyRate > 150f) {
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! + 15f
            scores[IaDiagnosisClass.WEAK_SIGNAL] = scores[IaDiagnosisClass.WEAK_SIGNAL]!! - 15f
        }

        // --- 2. Reglas Relacionadas con Latencia Local (Interferencia & Congestión LAN) ---
        val lanAvg = features.lanLatencyAvgMs
        val lanStd = features.lanLatencyStdMs
        val lanMax = features.lanLatencyMaxMs

        if (lanAvg > 25f) {
            scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 35f
            scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 20f
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 30f
        }

        if (lanStd > 12f) {
            scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 40f
            scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 25f
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 25f
        } else if (lanStd > 6f) {
            scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 15f
            scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 10f
        }

        if (lanMax > 100f) {
            scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 25f
            scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 20f
        }

        if (lanAvg < 6f && lanStd < 3f) {
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! + 20f
            scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! - 20f
        }

        // --- 3. Reglas Relacionadas con Latencia Externa & Proveedor (WAN) ---
        val wanAvg = features.wanLatencyAvgMs
        val wanStd = features.wanLatencyStdMs
        val wanMax = features.wanLatencyMaxMs
        val diffLanWan = features.diffLanWanAvgMs

        if (wanAvg > 120f && lanAvg < 15f) {
            scores[IaDiagnosisClass.ISP_PROBLEM] = scores[IaDiagnosisClass.ISP_PROBLEM]!! + 40f
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 30f
        }

        if (diffLanWan > 80f && lanAvg < 15f) {
            scores[IaDiagnosisClass.ISP_PROBLEM] = scores[IaDiagnosisClass.ISP_PROBLEM]!! + 45f
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 30f
        } else if (diffLanWan > 40f && lanAvg < 10f) {
            scores[IaDiagnosisClass.ISP_PROBLEM] = scores[IaDiagnosisClass.ISP_PROBLEM]!! + 20f
        }

        if (wanStd > 40f) {
            scores[IaDiagnosisClass.ISP_PROBLEM] = scores[IaDiagnosisClass.ISP_PROBLEM]!! + 20f
            scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 25f
        }

        if (wanMax > 250f && lanMax < 50f) {
            scores[IaDiagnosisClass.ISP_PROBLEM] = scores[IaDiagnosisClass.ISP_PROBLEM]!! + 25f
        }

        // --- 4. Reglas Relacionadas con Jitter ---
        val jitterAvg = features.jitterAvgMs
        val jitterMax = features.jitterMaxMs

        if (jitterAvg > 25f) {
            scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 35f
            if (lanAvg < 15f) {
                scores[IaDiagnosisClass.ISP_PROBLEM] = scores[IaDiagnosisClass.ISP_PROBLEM]!! + 15f
            }
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 20f
        } else if (jitterAvg > 10f) {
            scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 15f
        }

        if (jitterMax > 150f) {
            scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 20f
        }

        // --- 5. Reglas Relacionadas con Pérdida de Paquetes ---
        val loss = features.packetLossPercent

        if (loss > 8f) {
            if (lanAvg < 15f) {
                scores[IaDiagnosisClass.ISP_PROBLEM] = scores[IaDiagnosisClass.ISP_PROBLEM]!! + 55f
            } else {
                scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 35f
                scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 20f
            }
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 45f
        } else if (loss > 2f) {
            if (lanAvg < 15f) {
                scores[IaDiagnosisClass.ISP_PROBLEM] = scores[IaDiagnosisClass.ISP_PROBLEM]!! + 30f
            } else {
                scores[IaDiagnosisClass.NETWORK_UNSTABLE] = scores[IaDiagnosisClass.NETWORK_UNSTABLE]!! + 20f
                scores[IaDiagnosisClass.WIFI_INTERFERENCE] = scores[IaDiagnosisClass.WIFI_INTERFERENCE]!! + 10f
            }
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! - 20f
        }

        // --- 6. Línea de Base Altamente Saludable ---
        if (lanAvg < 8f && wanAvg < 60f && jitterAvg < 6f && loss < 1f && rssi >= -65f && rssi > -120f) {
            scores[IaDiagnosisClass.HEALTHY] = scores[IaDiagnosisClass.HEALTHY]!! + 45f
        }

        // --- 7. Selección del Diagnóstico con mayor Puntuación ---
        val bestEntry = scores.entries.maxByOrNull { it.value }
        val predictedClass = if (bestEntry != null && bestEntry.value > 0f) {
            bestEntry.key
        } else {
            IaDiagnosisClass.UNKNOWN
        }

        val maxScore = bestEntry?.value ?: 0f

        // --- 8. Cálculo de Confianza Basado en Score Absoluto ---
        val confidenceScore = when {
            predictedClass == IaDiagnosisClass.UNKNOWN -> 50
            maxScore >= 60f -> 95
            maxScore >= 45f -> 90
            maxScore >= 30f -> 80
            maxScore >= 15f -> 70
            else -> 60
        }

        // --- 9. Identificación de Características Top (Atribución) ---
        val topFeatures = identifyTopFeatures(predictedClass, features)

        Log.d(TAG, "Inferencia IA - Clase: $predictedClass, Score: $maxScore, Confianza: $confidenceScore%, Top Features: $topFeatures")

        return IaDiagnosisResult(predictedClass, confidenceScore, topFeatures)
    }

    /**
     * Identifica las 2 o 3 métricas que más influyeron para llegar a la conclusión diagnosticada.
     */
    private fun identifyTopFeatures(predictedClass: IaDiagnosisClass, features: NetworkFeatures): List<String> {
        val candidates = mutableListOf<Pair<String, Float>>()

        when (predictedClass) {
            IaDiagnosisClass.WEAK_SIGNAL -> {
                // Características de señal y PHY rate
                candidates.add("rssiAvgDbm" to if (features.rssiAvgDbm < -65f) -features.rssiAvgDbm else 0f)
                candidates.add("phyRateAvgMbps" to if (features.phyRateAvgMbps < 60f) (60f - features.phyRateAvgMbps) else 0f)
                candidates.add("rssiMinDbm" to if (features.rssiMinDbm < -75f) -features.rssiMinDbm else 0f)
                candidates.add("lanLatencyMaxMs" to if (features.lanLatencyMaxMs > 80f) features.lanLatencyMaxMs else 0f)
            }
            IaDiagnosisClass.WIFI_INTERFERENCE -> {
                // Inestabilidad en LAN a pesar de señal moderada/buena
                candidates.add("lanLatencyStdMs" to features.lanLatencyStdMs * 3f)
                candidates.add("lanLatencyMaxMs" to features.lanLatencyMaxMs)
                candidates.add("lanLatencyAvgMs" to features.lanLatencyAvgMs * 2f)
                candidates.add("rssiAvgDbm" to if (features.rssiAvgDbm < 0f) -features.rssiAvgDbm * 0.2f else 0f)
            }
            IaDiagnosisClass.ISP_PROBLEM -> {
                // Problemas localizados fuera de la red local
                candidates.add("packetLossPercent" to features.packetLossPercent * 5f)
                candidates.add("diffLanWanAvgMs" to features.diffLanWanAvgMs)
                candidates.add("wanLatencyAvgMs" to features.wanLatencyAvgMs * 0.5f)
                candidates.add("wanLatencyStdMs" to features.wanLatencyStdMs)
            }
            IaDiagnosisClass.NETWORK_UNSTABLE -> {
                // Alta fluctuación (jitter y pérdidas generales)
                candidates.add("jitterAvgMs" to features.jitterAvgMs * 4f)
                candidates.add("lanLatencyStdMs" to features.lanLatencyStdMs * 2f)
                candidates.add("packetLossPercent" to features.packetLossPercent * 3f)
                candidates.add("jitterMaxMs" to features.jitterMaxMs * 0.5f)
            }
            IaDiagnosisClass.HEALTHY -> {
                // Métricas optimizadas
                candidates.add("lanLatencyAvgMs" to if (features.lanLatencyAvgMs < 8f) 30f else 0f)
                candidates.add("wanLatencyAvgMs" to if (features.wanLatencyAvgMs < 60f) 20f else 0f)
                candidates.add("rssiAvgDbm" to if (features.rssiAvgDbm >= -65f) 10f else 0f)
            }
            IaDiagnosisClass.UNKNOWN -> {
                candidates.add("lanLatencyAvgMs" to features.lanLatencyAvgMs)
                candidates.add("wanLatencyAvgMs" to features.wanLatencyAvgMs)
            }
        }

        // Ordenar candidatos por importancia descendente y tomar las mejores 2 o 3 características
        return candidates
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(3)
            .let {
                if (it.size < 2) {
                    listOf("lanLatencyAvgMs", "wanLatencyAvgMs")
                } else {
                    it
                }
            }
    }
}
