package com.abdapps.visorwifi.ui.components.aplocator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.model.aplocator.ApInfo

/**
 * Tarjeta de selección e información del AP objetivo para AP Locator.
 *
 * Despliega:
 * - SSID de la red y BSSID seleccionado
 * - Fabricante aproximado (OUI lookup)
 * - Banda, frecuencia, canal y RSSI
 * - Lista de selección si existen múltiples BSSIDs visibles
 * - Botón "LOCALIZAR AP" para iniciar la calibración de 360°
 *
 * @param selectedAp     AP actualmente seleccionado.
 * @param availableAps   Lista de APs/BSSIDs disponibles para seleccionar.
 * @param onSelectAp     Callback para seleccionar un BSSID diferente.
 * @param onStartLocator Callback para iniciar la calibración de escaneo.
 * @param modifier       Modificador Compose.
 */
@Composable
fun ApSelectionCard(
    selectedAp: ApInfo?,
    availableAps: List<ApInfo>,
    onSelectAp: (ApInfo) -> Unit,
    onStartLocator: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }

    val ap = selectedAp ?: ApInfo(
        bssid = "--:--:--:--:--:--",
        ssid = "Buscando red...",
        rssi = -127,
        frequencyMHz = -1
    )

    val rssiQuality = when {
        ap.rssi >= -50 -> "Excelente"
        ap.rssi >= -60 -> "Muy Buena"
        ap.rssi >= -70 -> "Buena"
        ap.rssi >= -80 -> "Regular"
        ap.rssi > -127 -> "Débil"
        else -> "--"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121620))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = "AP SELECCIONADO PARA LOCALIZACIÓN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF536371),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // SSID y Fabricante
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SSID",
                        fontSize = 9.sp,
                        color = Color(0xFF536371),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ap.ssid,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00F0FF),
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (ap.manufacturer != "Sin información") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C2432))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = ap.manufacturer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E9AA8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // BSSID con selector si hay múltiples BSSIDs disponibles
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BSSID (DIRECCIÓN MAC AP)",
                        fontSize = 9.sp,
                        color = Color(0xFF536371),
                        fontWeight = FontWeight.Bold
                    )

                    if (availableAps.size > 1) {
                        Text(
                            text = "${availableAps.size} BSSIDs disponibles",
                            fontSize = 10.sp,
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showDropdown = !showDropdown }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161C26))
                        .then(
                            if (availableAps.size > 1) Modifier.clickable { showDropdown = !showDropdown }
                            else Modifier
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ap.bssid,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    if (availableAps.size > 1) {
                        Text(
                            text = if (showDropdown) "▲" else "▼",
                            fontSize = 12.sp,
                            color = Color(0xFF00F0FF)
                        )
                    }
                }

                // Desplegable simple de BSSIDs si el usuario hace clic
                if (showDropdown && availableAps.size > 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E2430))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    ) {
                        availableAps.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectAp(item)
                                        showDropdown = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = item.bssid,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.bssid == ap.bssid) Color(0xFF00F0FF) else Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${item.ssid} (${item.bandLabel})",
                                        fontSize = 10.sp,
                                        color = Color(0xFF8E9AA8)
                                    )
                                }
                                Text(
                                    text = "${item.rssi} dBm",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.bssid == ap.bssid) Color(0xFF00FF66) else Color(0xFF8E9AA8)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF1C2331))
            Spacer(modifier = Modifier.height(12.dp))

            // Parámetros técnicos: Banda/Frecuencia, Canal, RSSI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "BANDA", fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Text(
                        text = ap.bandLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F0FF),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "CANAL", fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Text(
                        text = if (ap.channel > 0) "Ch ${ap.channel}" else "--",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "RSSI ACTUAL", fontSize = 9.sp, color = Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Text(
                        text = if (ap.rssi > -127) "${ap.rssi} dBm" else "--",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ap.rssi >= -65) Color(0xFF00FF66) else Color(0xFFFF9900),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de acción principal "LOCALIZAR AP"
            Button(
                onClick = onStartLocator,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00F0FF),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "📡 LOCALIZAR AP",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
