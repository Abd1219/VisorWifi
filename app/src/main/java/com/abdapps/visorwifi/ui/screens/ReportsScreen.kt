package com.abdapps.visorwifi.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.ui.theme.VisorBorder
import com.abdapps.visorwifi.ui.theme.VisorPrimary
import com.abdapps.visorwifi.ui.theme.VisorSurface
import com.abdapps.visorwifi.ui.theme.VisorTextMuted

/** Honest empty state: no Room/history/PDF integration is claimed before those phases. */
@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("REPORTES", color = VisorPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).border(1.dp, VisorBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VisorSurface)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▤", fontSize = 42.sp, color = VisorTextMuted)
                Text("Historial próximamente", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = VisorPrimary)
                Text(
                    "Los reportes e historial persistente estarán disponibles en una fase posterior. Esta sección no muestra datos simulados.",
                    modifier = Modifier.padding(top = 10.dp),
                    color = VisorTextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
