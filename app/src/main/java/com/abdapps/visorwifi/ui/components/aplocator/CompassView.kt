package com.abdapps.visorwifi.ui.components.aplocator

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Brújula interactiva neo-brutalista para AP Locator.
 *
 * Distingue dos referencias angulares:
 * 1. **Azimut actual del teléfono**: Hacia dónde apunta físicamente el dispositivo (hace rotar la rosa).
 * 2. **Dirección estimada del AP**: Flecha que apunta hacia la zona donde se determinó la mejor señal.
 *
 * La flecha se compensa respecto al giro actual del teléfono para señalar físicamente la ubicación estimada.
 *
 * @param currentAzimuthDeg Azimut del dispositivo en grados (0° = Norte).
 * @param estimatedDirectionDeg Dirección estimada del AP en grados (opcional, null durante escaneo).
 * @param modifier Modificador de Compose.
 */
@Composable
fun CompassView(
    currentAzimuthDeg: Float,
    estimatedDirectionDeg: Double?,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Animación suave del azimut del teléfono para evitar giros bruscos
    val animatedPhoneAzimuth by animateFloatAsState(
        targetValue = currentAzimuthDeg,
        animationSpec = tween(durationMillis = 150),
        label = "PhoneAzimuthAnimation"
    )

    // Animación de la dirección estimada
    val targetEstimatedAzimuth = estimatedDirectionDeg?.toFloat() ?: 0f
    val animatedTargetAzimuth by animateFloatAsState(
        targetValue = targetEstimatedAzimuth,
        animationSpec = tween(durationMillis = 300),
        label = "TargetAzimuthAnimation"
    )

    Box(
        modifier = modifier
            .size(260.dp)
            .clip(CircleShape)
            .background(Color(0xFF0F141C))
            .border(
                2.dp,
                Brush.radialGradient(
                    colors = listOf(Color(0xFF00F0FF).copy(alpha = 0.4f), Color(0xFF0B0E14))
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f * 0.85f

            // ── 1. Anillo exterior y marcas angulares (Rotan opuesto al azimut del teléfono) ──
            rotate(-animatedPhoneAzimuth, pivot = center) {

                // Círculo base de la brújula
                drawCircle(
                    color = Color(0xFF1C2432),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Marcas angulares cada 15° y 30°
                for (angle in 0 until 360 step 15) {
                    val angleRad = Math.toRadians(angle.toDouble())
                    val isMajor = angle % 90 == 0
                    val isMedium = angle % 30 == 0

                    val tickLength = when {
                        isMajor -> 14.dp.toPx()
                        isMedium -> 10.dp.toPx()
                        else -> 6.dp.toPx()
                    }

                    val innerRadius = radius - tickLength
                    val startX = center.x + innerRadius * sin(angleRad).toFloat()
                    val startY = center.y - innerRadius * cos(angleRad).toFloat()
                    val endX = center.x + radius * sin(angleRad).toFloat()
                    val endY = center.y - radius * cos(angleRad).toFloat()

                    val strokeWidth = if (isMajor) 3.dp.toPx() else if (isMedium) 2.dp.toPx() else 1.dp.toPx()
                    val tickColor = if (isMajor) Color(0xFF00F0FF) else if (isMedium) Color(0xFF8E9AA8) else Color(0xFF334155)

                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth
                    )
                }

                // Puntos cardinales N, E, S, W
                val cardinalLabels = listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270)
                for ((label, angle) in cardinalLabels) {
                    val angleRad = Math.toRadians(angle.toDouble())
                    val labelRadius = radius - 24.dp.toPx()
                    val lx = center.x + labelRadius * sin(angleRad).toFloat()
                    val ly = center.y - labelRadius * cos(angleRad).toFloat()

                    val color = if (label == "N") Color(0xFFFF3366) else Color(0xFF00F0FF)
                    val textLayout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    )

                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            lx - textLayout.size.width / 2f,
                            ly - textLayout.size.height / 2f
                        )
                    )
                }
            }

            // ── 2. Flecha de dirección estimada del AP ──────────────────────────
            if (estimatedDirectionDeg != null) {
                // Compensar la dirección estimada respecto a la orientación actual del teléfono
                val relativeAngle = animatedTargetAzimuth - animatedPhoneAzimuth

                rotate(relativeAngle, pivot = center) {

                    // Flecha principal (Cian vibrante #00F0FF)
                    val arrowPath = Path().apply {
                        moveTo(center.x, center.y - radius * 0.75f) // Punta superior
                        lineTo(center.x + 18.dp.toPx(), center.y + 12.dp.toPx())
                        lineTo(center.x, center.y - 4.dp.toPx()) // Hendidura base
                        lineTo(center.x - 18.dp.toPx(), center.y + 12.dp.toPx())
                        close()
                    }

                    // Sombra/Glow de la flecha
                    drawPath(
                        path = arrowPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF00F0FF), Color(0xFF0099FF))
                        )
                    )

                    // Borde de realce de la flecha
                    drawPath(
                        path = arrowPath,
                        color = Color.White,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Icono/Punto AP en el extremo de la dirección
                    drawCircle(
                        color = Color(0xFF00FF66),
                        radius = 6.dp.toPx(),
                        center = Offset(center.x, center.y - radius * 0.82f)
                    )
                }
            }

            // ── 3. Indicador central del dispositivo (Teléfono en mano) ────────
            drawCircle(
                color = Color(0xFF161C26),
                radius = 20.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color(0xFF00F0FF),
                radius = 20.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Marca de orientación del teléfono (pequeña flecha fija hacia arriba)
            val phoneFrontPath = Path().apply {
                moveTo(center.x, center.y - 12.dp.toPx())
                lineTo(center.x + 6.dp.toPx(), center.y - 2.dp.toPx())
                lineTo(center.x - 6.dp.toPx(), center.y - 2.dp.toPx())
                close()
            }
            drawPath(path = phoneFrontPath, color = Color(0xFF00F0FF))
        }
    }
}
