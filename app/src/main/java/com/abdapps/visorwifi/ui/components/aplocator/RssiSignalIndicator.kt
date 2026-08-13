package com.abdapps.visorwifi.ui.components.aplocator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Indicador visual de señal en tiempo real para AP Locator.
 *
 * Muestra el RSSI numérico en dBm, barra graduada de intensidad,
 * nivel cualitativo de señal y estadísticas de variación (actual, promedio, mejor).
 *
 * @param currentRssi RSSI instantáneo en dBm (-127 si no disponible).
 * @param bestRssi    Mejor RSSI registrado en la sesión (dBm).
 * @param avgRssi     RSSI promedio registrado en la sesión (dBm).
 * @param modifier    Modificador Compose.
 */
@Composable
fun RssiSignalIndicator(
    currentRssi: Int,
    bestRssi: Int,
    avgRssi: Float,
    modifier: Modifier = Modifier
) {
    val isValid = currentRssi > -127

    // Calificación cualitativa
    val qualityLabel = when {
        !isValid -> "--"
        currentRssi >= -50 -> "Excelente"
        currentRssi >= -60 -> "Muy Buena"
        currentRssi >= -70 -> "Buena"
        currentRssi >= -80 -> "Regular"
        else -> "Débil"
    }

    // Color cualitativo dinámico
    val qualityColor = when (qualityLabel) {
        "Excelente" -> Color(0xFF00FF66)
        "Muy Buena" -> Color(0xFF00F0FF)
        "Buena"     -> Color(0xFFFFD700)
        "Regular"   -> Color(0xFFFF9900)
        "Débil"     -> Color(0xFFFF3366)
        else        -> Color(0xFF536371)
    }

    // Mapeo de RSSI (-90 a -30 dBm) a porcentaje (0 a 100%)
    val signalPercent = if (isValid) {
        ((currentRssi - (-90)) / 60.0f).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121620))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "INTENSIDAD DE SEÑAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF536371),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isValid) "$currentRssi dBm" else "-- dBm",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = qualityColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(qualityColor.copy(alpha = 0.15f))
                        .border(1.dp, qualityColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = qualityLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = qualityColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra de intensidad graduada (10 segmentos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val filledSegments = (signalPercent * 10).toInt()
                for (i in 0 until 10) {
                    val isActive = i < filledSegments
                    val segColor = if (isActive) qualityColor else Color(0xFF1E293B)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(segColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fila de estadísticas: Actual, Promedio, Mejor registrado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "RSSI ACTUAL",
                    value = if (isValid) "$currentRssi dBm" else "--",
                    color = Color.White
                )
                StatItem(
                    label = "PROMEDIO",
                    value = if (avgRssi > -127f) String.format(Locale.US, "%.1f dBm", avgRssi) else "--",
                    color = Color(0xFF00F0FF)
                )
                StatItem(
                    label = "MEJOR REGISTRADO",
                    value = if (bestRssi > -127) "$bestRssi dBm" else "--",
                    color = Color(0xFF00FF66)
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column {
        Text(text = label, fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}
