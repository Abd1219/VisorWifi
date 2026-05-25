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

/**
 * Caja de registro (Log) que expone los cambios de antena/itinerancia (roaming)
 * y transiciones de SSID de manera ordenada, mostrando los eventos más recientes en la parte superior.
 *
 * @param roamEvents Lista de cadenas de texto con los eventos históricos formateados.
 */
@Composable
fun RoamingEventLog(roamEvents: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1C2331), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F18))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EVENTOS DE RED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF536371),
                    letterSpacing = 1.sp
                )
                // Contador totalizador de eventos de red capturados
                if (roamEvents.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C2331))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${roamEvents.size} evento${if (roamEvents.size != 1) "s" else ""}",
                            fontSize = 10.sp,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (roamEvents.isEmpty()) {
                Text(
                    text = "Sin cambios de red detectados aún...",
                    fontSize = 11.sp,
                    color = Color(0xFF536371),
                    fontFamily = FontFamily.Monospace
                )
            } else {
                // Desplegar un máximo de 10 eventos en orden inverso (del más reciente al más antiguo)
                roamEvents.reversed().take(10).forEach { event ->
                    val isRoaming = event.contains("Roaming")
                    val eventColor = if (isRoaming)
                        Color(0xFFFFD700) // Amarillo para cambio de antena AP
                    else
                        Color(0xFF00F0FF) // Cian para cambio de red SSID
                    Text(
                        text = event,
                        fontSize = 10.sp,
                        color = eventColor,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}
