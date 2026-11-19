package com.abdapps.visorwifi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Solo cambia la densidad de información presentada; no altera telemetría ni diagnóstico. */
enum class MonitorViewMode { SUMMARY, TECHNICAL }

@Composable
fun MonitorViewModeSwitcher(
    selectedMode: MonitorViewMode,
    onModeSelected: (MonitorViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121620))
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        MonitorModeOption(
            text = "RESUMEN",
            selected = selectedMode == MonitorViewMode.SUMMARY,
            onClick = { onModeSelected(MonitorViewMode.SUMMARY) },
            modifier = Modifier.weight(1f)
        )
        MonitorModeOption(
            text = "TÉCNICA",
            selected = selectedMode == MonitorViewMode.TECHNICAL,
            onClick = { onModeSelected(MonitorViewMode.TECHNICAL) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MonitorModeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color(0xFF00F0FF) else Color.Transparent)
            .semantics { role = Role.Tab }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFF0B0E14) else Color(0xFF8E9AA8),
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            letterSpacing = 0.7.sp
        )
    }
}
