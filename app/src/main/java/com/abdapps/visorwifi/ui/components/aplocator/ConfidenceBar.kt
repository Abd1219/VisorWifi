package com.abdapps.visorwifi.ui.components.aplocator

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componente que muestra la barra e indicador del nivel de confianza de estimación (0–100%).
 *
 * El color cambia dinámicamente según el nivel de confianza:
 * - 70–100%: Verde (#00FF66) — Confianza alta
 * - 40–69%:  Amarillo (#FFD700) — Confianza moderada
 * - 0–39%:   Rojo (#FF3366) — Confianza baja / imprecisa
 *
 * @param confidencePercent Porcentaje de confianza (0 a 100).
 * @param message           Mensaje descriptivo legible sobre la fiabilidad de la medición.
 * @param modifier          Modificador Compose.
 */
@Composable
fun ConfidenceBar(
    confidencePercent: Int,
    message: String,
    modifier: Modifier = Modifier
) {
    val clampedConfidence = confidencePercent.coerceIn(0, 100)
    val fraction = clampedConfidence / 100.0f

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 400),
        label = "ConfidenceBarAnimation"
    )

    val confidenceColor = when {
        clampedConfidence >= 70 -> Color(0xFF00FF66)
        clampedConfidence >= 40 -> Color(0xFFFFD700)
        else                    -> Color(0xFFFF3366)
    }

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
                Text(
                    text = "CONFIANZA DE ESTIMACIÓN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF536371),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$clampedConfidence%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = confidenceColor,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barra de progreso de confianza
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF1C2432))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .clip(RoundedCornerShape(5.dp))
                        .background(confidenceColor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mensaje descriptivo
            Text(
                text = message,
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF),
                lineHeight = 16.sp
            )
        }
    }
}
