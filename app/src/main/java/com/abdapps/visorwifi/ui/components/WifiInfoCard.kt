package com.abdapps.visorwifi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.NetworkMonitorService
import com.abdapps.visorwifi.model.LatencyPoint

/**
 * Componente Compose para visualizar detalles técnicos del enlace WiFi activo:
 * SSID de red, dirección BSSID del Access Point, Banda de Frecuencia física,
 * fuerza del RSSI calificado y tasas físicas de transmisión (PHY TX/RX).
 *
 * @param latestPoint Último punto telemétrico capturado que contiene los datos instantáneos.
 * @param service Instancia activa del servicio de monitoreo para formateadores de frecuencia y RSSI.
 */
@Composable
fun WifiInfoCard(latestPoint: LatencyPoint?, service: NetworkMonitorService?) {
    // Resolver la banda comercial a partir de la frecuencia física del canal
    val band = latestPoint?.let {
        if (it.frequency > 0) service?.frequencyToBand(it.frequency) ?: "" else ""
    } ?: ""
    val rssi = latestPoint?.rssi ?: -127
    val rssiQuality = if (rssi > -127) service?.rssiToQuality(rssi) ?: "--" else "--"
    val rssiText = if (rssi > -127) "$rssi dBm" else "--"
    val txSpeed = latestPoint?.txLinkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" } ?: "--"
    val rxSpeed = latestPoint?.rxLinkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" } ?: "--"
    val ssidText = latestPoint?.ssid?.takeIf { it.isNotBlank() } ?: "Sin permiso de ubicación"
    val bssidText = latestPoint?.bssid?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" } ?: "--"

    // Selección dinámica de color en base al nivel cualitativo de la señal
    val rssiColor = when (rssiQuality) {
        "Excelente" -> Color(0xFF00FF66)
        "Muy Buena" -> Color(0xFF00F0FF)
        "Buena" -> Color(0xFFFFD700)
        "Regular" -> Color(0xFFFF9900)
        "Débil" -> Color(0xFFFF3366)
        else -> Color(0xFF536371)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color(0x2000FF66),
                        Color(0x0500FF66)
                    )
                ),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121620))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CONEXIÓN WiFi",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF536371),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Red (SSID) y MAC del AP de destino (BSSID)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    WifiInfoRow(icon = "📶", label = "RED (SSID)", value = ssidText, valueColor = Color(0xFF00F0FF))
                    Spacer(modifier = Modifier.height(8.dp))
                    WifiInfoRow(icon = "🔗", label = "AP (BSSID)", value = bssidText, valueColor = Color(0xFF8E9AA8))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF1C2331))
            Spacer(modifier = Modifier.height(12.dp))

            // Fila de parámetros físicos: Banda, Calidad y Velocidad del Enlace físico
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Frecuencia física
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "BANDA", fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = band.ifEmpty { "--" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00F0FF),
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                // Nivel cualitativo y cuantitativo del RSSI
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "SEÑAL", fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rssiText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = rssiColor,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(text = rssiQuality, fontSize = 10.sp, color = rssiColor.copy(alpha = 0.7f))
                }
                
                // Velocidad PHY de subida y bajada en tiempo real
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "VELOCIDAD PHY", fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "↑", fontSize = 11.sp, color = Color(0xFF00FF66))
                        Text(text = txSpeed, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "↓", fontSize = 11.sp, color = Color(0xFF00F0FF))
                        Text(text = rxSpeed, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

/**
 * Celda básica para renderizar pares clave-valor dentro de la tarjeta de WiFi.
 */
@Composable
fun WifiInfoRow(
    icon: String,
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Column {
        Text(text = label, fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = icon, fontSize = 12.sp)
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor, fontFamily = FontFamily.Monospace)
        }
    }
}
