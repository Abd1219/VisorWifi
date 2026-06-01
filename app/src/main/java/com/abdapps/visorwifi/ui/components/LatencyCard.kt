package com.abdapps.visorwifi.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componente Compose para renderizar métricas individuales de latencia en un formato estético premium.
 * Muestra el valor de latencia actual (LAN o WAN), una etiqueta, una subetiqueta aclaratoria y
 * un indicador visual de color correspondiente a la red medida.
 *
 * @param modifier Modificador Compose de diseño.
 * @param label Título o identificador superior de la tarjeta (ej. "LATENCIA LAN").
 * @param sublabel Descripción secundaria o aclaratoria de la tarjeta (ej. "Acceso Local").
 * @param accentColor Color característico de acento para el indicador y el borde degradado.
 * @param value Valor actual de la latencia formateado en milisegundos (ej. "4.5 ms" o "Timeout").
 * @param packetLoss Porcentaje opcional de pérdida de paquetes para desplegar un badge de advertencia.
 */
@Composable
fun LatencyCard(
    modifier: Modifier = Modifier,
    label: String,
    sublabel: String,
    accentColor: Color,
    value: String,
    packetLoss: Float? = null
) {
    Card(
        modifier = modifier.border(
            1.dp,
            Brush.linearGradient(colors = listOf(accentColor.copy(alpha = 0.12f), accentColor.copy(alpha = 0.02f))),
            RoundedCornerShape(16.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121620))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            // Indicador de color de la métrica y badge superior derecho si hay pérdida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor)
                )
                
                if (packetLoss != null && packetLoss > 0f) {
                    val badgeColor = if (packetLoss > 5f) Color(0xFFFF3366) else Color(0xFFFF9900)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.12f))
                            .border(0.5.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "Pérdida: %.0f%%", packetLoss),
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Títulos y descripciones
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E9AA8)
            )
            Text(
                text = sublabel,
                fontSize = 10.sp,
                color = Color(0xFF536371)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Valor telemétrico instantáneo
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
