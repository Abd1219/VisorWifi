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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.model.aplocator.MeasurementPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Muestra la lista de puntos de medición registrados durante la sesión en memoria.
 *
 * Permite a un técnico desplazarse físicamente por el área, realizar varias mediciones
 * y comparar los azimuts y RSSI obtenidos desde cada punto.
 *
 * @param history    Lista de [MeasurementPoint] registrados.
 * @param onClear    Callback para limpiar el historial.
 * @param modifier   Modificador Compose.
 */
@Composable
fun MeasurementHistoryList(
    history: List<MeasurementPoint>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

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
                    text = "HISTORIAL DE PUNTOS (${history.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF536371),
                    letterSpacing = 1.sp
                )

                TextButton(onClick = onClear) {
                    Text("LIMPIAR", fontSize = 10.sp, color = Color(0xFFFF3366), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            history.forEachIndexed { index, point ->
                val pointNumber = index + 1
                val timeStr = timeFormat.format(Date(point.timestamp))

                val confidenceColor = when {
                    point.confidencePercent >= 70 -> Color(0xFF00FF66)
                    point.confidencePercent >= 40 -> Color(0xFFFFD700)
                    else -> Color(0xFFFF3366)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF161C26))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Punto #$pointNumber • $timeStr",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E9AA8)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "Dirección: %.0f°", point.estimatedAzimuthDeg),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00F0FF),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "|  Mejor: ${point.bestRssi} dBm",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(confidenceColor.copy(alpha = 0.15f))
                                .border(1.dp, confidenceColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${point.confidencePercent}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = confidenceColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
