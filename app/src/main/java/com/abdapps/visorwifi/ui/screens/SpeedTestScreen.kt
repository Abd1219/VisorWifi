package com.abdapps.visorwifi.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.speedtest.connectivity.ConnectivityChange
import com.abdapps.visorwifi.speedtest.connectivity.NetworkSnapshot
import com.abdapps.visorwifi.speedtest.connectivity.NetworkTransport
import com.abdapps.visorwifi.speedtest.connectivity.WifiInfoAvailability
import com.abdapps.visorwifi.speedtest.domain.SpeedTestResult
import com.abdapps.visorwifi.speedtest.domain.SpeedTestStatus
import com.abdapps.visorwifi.ui.theme.VisorBorder
import com.abdapps.visorwifi.ui.theme.VisorError
import com.abdapps.visorwifi.ui.theme.VisorHealthy
import com.abdapps.visorwifi.ui.theme.VisorPrimary
import com.abdapps.visorwifi.ui.theme.VisorSecondary
import com.abdapps.visorwifi.ui.theme.VisorSurface
import com.abdapps.visorwifi.ui.theme.VisorTextMuted
import com.abdapps.visorwifi.viewmodel.SpeedTestPresentation
import com.abdapps.visorwifi.viewmodel.SpeedTestUiState
import com.abdapps.visorwifi.viewmodel.SpeedTestViewModel
import java.util.Locale

@Composable
fun SpeedTestScreen(viewModel: SpeedTestViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.onScreenVisible() }
    DisposableEffect(Unit) { onDispose { viewModel.onScreenHidden() } }

    val running = SpeedTestPresentation.isRunning(state.status)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("SPEED TEST", color = VisorPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Text("Medición HTTP independiente del monitor ICMP", color = VisorTextMuted, fontSize = 12.sp)
        ConnectivityCard(state.currentSnapshot)

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, VisorBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VisorSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (running) CircularProgressIndicator(color = VisorPrimary)
                Text(SpeedTestPresentation.phaseLabel(state.status), modifier = Modifier.padding(top = 10.dp), color = if (state.status == SpeedTestStatus.FAILED) VisorError else VisorPrimary, fontWeight = FontWeight.Bold)
                if (!state.isServerConfigured) {
                    Text("Servidor no configurado. La prueba real no está disponible", modifier = Modifier.padding(top = 8.dp), color = VisorTextMuted, fontSize = 12.sp)
                }
                Button(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    onClick = { if (running) viewModel.cancelTest() else viewModel.startTest() },
                    enabled = running || state.isServerConfigured,
                    colors = ButtonDefaults.buttonColors(containerColor = if (running) VisorError else VisorPrimary, contentColor = if (running) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) { Text(if (running) "CANCELAR PRUEBA" else "INICIAR PRUEBA", fontWeight = FontWeight.Black) }
                state.message?.let { Text(it, modifier = Modifier.padding(top = 10.dp), color = VisorTextMuted, fontSize = 12.sp) }
            }
        }
        ResultCard(state.result)
        if (state.connectivityChanges.isNotEmpty()) ConnectivityWarningCard(state.connectivityChanges)
    }
}

@Composable private fun ConnectivityCard(snapshot: NetworkSnapshot) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VisorSurface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("CONECTIVIDAD ACTUAL", color = VisorPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            DetailRow("Transporte efectivo", transportLabel(snapshot.effectiveTransport))
            DetailRow("Internet", if (snapshot.internetAvailable) "Disponible" else "No disponible")
            DetailRow("Validación", if (snapshot.internetValidated) "Validada" else "No validada")
            DetailRow("Uso medido", if (snapshot.isMetered) "Sí" else "No")
            if (snapshot.isVpn) DetailRow("VPN", "Activa · físico: ${snapshot.underlyingTransports.joinToString().ifBlank { "No disponible" }}")
            if (snapshot.wifi.availability == WifiInfoAvailability.AVAILABLE) {
                DetailRow("Wi-Fi", snapshot.wifi.ssid ?: "No disponible")
                DetailRow("RSSI", snapshot.wifi.rssiDbm?.let { "$it dBm" } ?: "No disponible")
                DetailRow("Frecuencia", snapshot.wifi.frequencyMHz?.let { "$it MHz" } ?: "No disponible")
                DetailRow("Enlace", snapshot.wifi.linkSpeedMbps?.let { "$it Mbps" } ?: "No disponible")
            } else if (snapshot.hasWifi) DetailRow("Wi-Fi", "Información no disponible")
        }
    }
}

@Composable private fun ResultCard(result: SpeedTestResult?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VisorSurface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("RESULTADOS HTTP", color = VisorSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("No son latencia ICMP ni velocidad PHY Wi-Fi", color = VisorTextMuted, fontSize = 11.sp)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("DOWNLOAD", result?.downloadMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "N/D")
                Metric("UPLOAD", result?.uploadMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "N/D")
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("LATENCIA HTTP", result?.latencyAverageMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "N/D")
                Metric("JITTER HTTP", result?.jitterMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "N/D")
            }
            result?.server?.let { Text("Servidor: ${it.name}", modifier = Modifier.padding(top = 10.dp), color = VisorTextMuted, fontSize = 11.sp) }
        }
    }
}

@Composable private fun ConnectivityWarningCard(changes: Set<ConnectivityChange>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF30250A))) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("CAMBIOS DE CONECTIVIDAD", color = androidx.compose.ui.graphics.Color(0xFFFFB703), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("La prueba no se canceló automáticamente. ${changes.joinToString { changeLabel(it) }}", color = VisorTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable private fun DetailRow(label: String, value: String) = Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = VisorTextMuted, fontSize = 12.sp)
    Spacer(Modifier.width(12.dp))
    Text(value, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
@Composable private fun Metric(label: String, value: String) = Column { Text(label, color = VisorTextMuted, fontSize = 10.sp); Text(value, color = VisorHealthy, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
private fun transportLabel(transport: NetworkTransport) = when (transport) { NetworkTransport.WIFI -> "Wi-Fi"; NetworkTransport.CELLULAR -> "Datos móviles"; NetworkTransport.VPN -> "VPN"; NetworkTransport.OTHER -> "Otro"; NetworkTransport.NONE -> "Sin conexión" }
private fun changeLabel(change: ConnectivityChange) = when (change) { ConnectivityChange.NETWORK_LOST -> "Se perdió la red"; ConnectivityChange.NETWORK_RESTORED -> "Se restauró la red"; ConnectivityChange.EFFECTIVE_TRANSPORT_CHANGED -> "Cambió el transporte"; ConnectivityChange.UNDERLYING_NETWORK_CHANGED -> "Cambió la red subyacente"; ConnectivityChange.VPN_STATE_CHANGED -> "Cambió la VPN"; ConnectivityChange.INTERNET_AVAILABILITY_CHANGED -> "Cambió Internet"; ConnectivityChange.INTERNET_VALIDATION_CHANGED -> "Cambió la validación" }
