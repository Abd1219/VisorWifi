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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.model.aplocator.ApInfo
import java.util.Locale

/**
 * Vista del estado SCANNING (Calibración / Rotación 360°).
 *
 * Muestra:
 * - Instrucciones claras para el técnico ("Gira lentamente el teléfono 360°").
 * - Brújula interactiva en tiempo real con el azimut actual.
 * - Progreso circular/lineal de rotación (0°–360° / 0–100%).
 * - Lectura instantánea de RSSI y conteo de muestras capturadas.
 * - Botones de control "FINALIZAR ESCANEO" y "CANCELAR".
 *
 * @param selectedAp         AP objetivo seleccionado.
 * @param currentAzimuthDeg  Azimut instantáneo en grados del dispositivo.
 * @param currentRssi        RSSI instantáneo en dBm.
 * @param rotationProgress   Porcentaje de progreso de cobertura (0–100%).
 * @param coveredDegrees     Grados cubiertos (0.0 a 360.0°).
 * @param onFinishScan       Callback para finalizar y calcular estimación.
 * @param onCancelScan       Callback para cancelar la sesión de escaneo.
 * @param modifier           Modificador Compose.
 */
@Composable
fun ScanProgressView(
    selectedAp: ApInfo?,
    currentAzimuthDeg: Float,
    currentRssi: Int,
    rotationProgress: Int,
    coveredDegrees: Double,
    onFinishScan: () -> Unit,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Instrucción principal destacada
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161C26))
                    .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🔄 Gira lentamente el teléfono 360°",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00F0FF),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Mantén el dispositivo horizontal y realiza un giro completo",
                        fontSize = 11.sp,
                        color = Color(0xFF8E9AA8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Brújula en tiempo real (flecha estimada nula durante el escaneo)
            CompassView(
                currentAzimuthDeg = currentAzimuthDeg,
                estimatedDirectionDeg = null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fila de datos telemétricos en tiempo real: Azimut, RSSI, Progreso
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScanStatBox(
                    label = "AZIMUT ACTUAL",
                    value = String.format(Locale.US, "%.0f°", currentAzimuthDeg),
                    color = Color.White
                )
                ScanStatBox(
                    label = "RSSI ACTUAL",
                    value = if (currentRssi > -127) "$currentRssi dBm" else "--",
                    color = if (currentRssi >= -65) Color(0xFF00FF66) else Color(0xFFFF9900)
                )
                ScanStatBox(
                    label = "COBERTURA",
                    value = String.format(Locale.US, "%.0f°", coveredDegrees),
                    color = Color(0xFF00F0FF)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelScan,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CANCELAR", fontSize = 12.sp, color = Color(0xFF8E9AA8))
                }

                Button(
                    onClick = onFinishScan,
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00F0FF),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "CALCULAR DIRECCIÓN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanStatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}
