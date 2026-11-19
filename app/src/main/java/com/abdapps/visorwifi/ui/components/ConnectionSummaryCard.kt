package com.abdapps.visorwifi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.NetworkMonitorService
import com.abdapps.visorwifi.model.LatencyPoint

/** Lectura para cliente final derivada únicamente de la última muestra disponible. */
@Composable
fun ConnectionSummaryCard(latestPoint: LatencyPoint?, service: NetworkMonitorService?) {
    val rssi = latestPoint?.rssi ?: -127
    val signal = if (rssi > -127) service?.rssiToQuality(rssi) ?: "--" else "Sin datos"
    val signalColor = when (signal) {
        "Excelente" -> Color(0xFF00FF66)
        "Muy Buena", "Buena" -> Color(0xFF00F0FF)
        "Regular" -> Color(0xFFFFB703)
        "Débil" -> Color(0xFFFF3366)
        else -> Color(0xFF8E9AA8)
    }
    val network = latestPoint?.ssid?.takeIf { it.isNotBlank() } ?: "Red Wi-Fi no disponible"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121620))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("CONEXIÓN WI-FI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E9AA8), letterSpacing = 1.sp)
            Text(network, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = FontFamily.Monospace)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryMetric("CALIDAD DE SEÑAL", signal, signalColor)
                SummaryMetric("NIVEL", if (rssi > -127) "$rssi dBm" else "--", Color.White)
                SummaryMetric("BANDA", latestPoint?.frequency?.let { service?.frequencyToBand(it) }?.ifBlank { "--" } ?: "--", Color(0xFF00F0FF))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF536371))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color, fontFamily = FontFamily.Monospace)
    }
}
