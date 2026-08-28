package com.abdapps.visorwifi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.abdapps.visorwifi.AppScreen

/**
 * Componente Neo-Brutalista de selección de modos de la aplicación (MONITOR vs AP LOCATOR).
 *
 * Mantiene la identidad estética del proyecto:
 * - Fondo oscuro `#121620`
 * - Resaltado de modo activo con color primario Cian `#00F0FF`
 * - Bordes definidos en `#1E293B`
 *
 * @param currentScreen Modo actualmente seleccionado.
 * @param onScreenSelected Callback emitido al presionar un modo.
 * @param modifier Modificador Compose.
 */
@Composable
fun ModeSwitcher(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121620))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        // ── Opción 1: MONITOR ───────────────────────────────────────────────
        val isMonitorActive = currentScreen == AppScreen.MONITOR
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isMonitorActive) Color(0xFF00F0FF) else Color.Transparent)
                .clickable { onScreenSelected(AppScreen.MONITOR) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📊 MONITOR",
                fontSize = 12.sp,
                fontWeight = if (isMonitorActive) FontWeight.Black else FontWeight.Bold,
                color = if (isMonitorActive) Color.Black else Color(0xFF8E9AA8),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        // ── Opción 2: AP LOCATOR ─────────────────────────────────────────────
        val isLocatorActive = currentScreen == AppScreen.AP_LOCATOR
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isLocatorActive) Color(0xFF00F0FF) else Color.Transparent)
                .clickable { onScreenSelected(AppScreen.AP_LOCATOR) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📡 AP LOCATOR",
                fontSize = 12.sp,
                fontWeight = if (isLocatorActive) FontWeight.Black else FontWeight.Bold,
                color = if (isLocatorActive) Color.Black else Color(0xFF8E9AA8),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}
