package com.abdapps.visorwifi.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abdapps.visorwifi.NetworkMonitorService
import com.abdapps.visorwifi.model.LatencyPoint
import com.abdapps.visorwifi.ui.components.aplocator.ApSelectionCard
import com.abdapps.visorwifi.ui.components.aplocator.CompassView
import com.abdapps.visorwifi.ui.components.aplocator.ConfidenceBar
import com.abdapps.visorwifi.ui.components.aplocator.MeasurementHistoryList
import com.abdapps.visorwifi.ui.components.aplocator.RssiSignalIndicator
import com.abdapps.visorwifi.ui.components.aplocator.ScanProgressView
import com.abdapps.visorwifi.viewmodel.ApLocatorScreenState
import com.abdapps.visorwifi.viewmodel.ApLocatorViewModel
import java.util.Locale

/**
 * Pantalla principal del módulo AP Locator.
 *
 * Desarrollada de forma declarativa en Jetpack Compose.
 * Administra los tres estados del flujo:
 * 1. **SELECT_AP**: Selección e información técnica del BSSID objetivo.
 * 2. **SCANNING**: Calibración de rotación de 360° acumulando muestras de RSSI + orientación.
 * 3. **RESULT**: Brújula con la dirección estimada, indicador de señal y porcentaje de confianza.
 *
 * @param modifier     Modificador Compose.
 * @param service      Instancia activa del servicio de monitoreo.
 * @param latestPoint  Último punto telemétrico instantáneo emitido por el servicio.
 * @param onNavigateBack Callback para regresar a la pantalla principal de monitoreo.
 */
@Composable
fun ApLocatorScreen(
    modifier: Modifier = Modifier,
    service: NetworkMonitorService?,
    latestPoint: LatencyPoint?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val locatorViewModel: ApLocatorViewModel = viewModel()

    val screenState by locatorViewModel.screenState.collectAsState()
    val selectedAp by locatorViewModel.selectedAp.collectAsState()
    val availableAps by locatorViewModel.availableAps.collectAsState()
    val currentAzimuth by locatorViewModel.currentAzimuth.collectAsState()
    val rotationProgress by locatorViewModel.rotationProgress.collectAsState()
    val coveredDegrees by locatorViewModel.coveredDegrees.collectAsState()
    val directionEstimate by locatorViewModel.directionEstimate.collectAsState()
    val measurementHistory by locatorViewModel.measurementHistory.collectAsState()
    val isSensorAvailable by locatorViewModel.isSensorAvailable.collectAsState()
    val sensorErrorMessage by locatorViewModel.sensorErrorMessage.collectAsState()

    // Inicializar sensores y escuchar ciclo de vida de la pantalla
    LaunchedEffect(Unit) {
        locatorViewModel.initSensors(context)
        locatorViewModel.startListeningSensors()
    }

    DisposableEffect(Unit) {
        onDispose {
            locatorViewModel.stopListeningSensors()
        }
    }

    // Actualizar AP conectado con cada nueva lectura de telemetría del servicio
    LaunchedEffect(latestPoint) {
        locatorViewModel.updateConnectedAp(latestPoint, service, context)
        if (screenState == ApLocatorScreenState.SCANNING) {
            locatorViewModel.onNewRssiSample(latestPoint)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Cabecera AP Locator con Botón de Regreso ──────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AP LOCATOR",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00F0FF),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Estimación de Dirección de Access Point",
                    fontSize = 12.sp,
                    color = Color(0xFF8E9AA8)
                )
            }

            OutlinedButton(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("VOLVER", fontSize = 11.sp, color = Color(0xFF8E9AA8), fontWeight = FontWeight.Bold)
            }
        }

        // ── Banner de Advertencia si los Sensores no están disponibles ───────
        if (!isSensorAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1219))
            ) {
                Text(
                    text = "⚠️ ${sensorErrorMessage ?: "El dispositivo no dispone del sensor necesario para realizar esta medición."}",
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B81),
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── ESTADO 1: SELECT_AP ───────────────────────────────────────────────
        if (screenState == ApLocatorScreenState.SELECT_AP) {

            ApSelectionCard(
                selectedAp = selectedAp,
                availableAps = availableAps,
                onSelectAp = { locatorViewModel.selectAp(it) },
                onStartLocator = {
                    if (isSensorAvailable) {
                        locatorViewModel.startScan()
                    } else {
                        Toast.makeText(context, "No es posible iniciar: faltan sensores de orientación.", Toast.LENGTH_LONG).show()
                    }
                }
            )

            // Banner explicativo sobre honestidad técnica de la estimación
            TechnicalDisclaimerCard()

            // Historial de mediciones realizadas previamente en la sesión
            MeasurementHistoryList(
                history = measurementHistory,
                onClear = { locatorViewModel.clearHistory() }
            )
        }

        // ── ESTADO 2: SCANNING ───────────────────────────────────────────────
        if (screenState == ApLocatorScreenState.SCANNING) {

            ScanProgressView(
                selectedAp = selectedAp,
                currentAzimuthDeg = currentAzimuth,
                currentRssi = latestPoint?.rssi ?: -127,
                rotationProgress = rotationProgress,
                coveredDegrees = coveredDegrees,
                onFinishScan = { locatorViewModel.finishScan() },
                onCancelScan = { locatorViewModel.cancelScan() }
            )
        }

        // ── ESTADO 3: RESULT ──────────────────────────────────────────────────
        if (screenState == ApLocatorScreenState.RESULT) {

            val estimate = directionEstimate

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "DIRECCIÓN ESTIMADA DEL AP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF536371),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Brújula con la flecha apuntando a la dirección estimada
                    CompassView(
                        currentAzimuthDeg = currentAzimuth,
                        estimatedDirectionDeg = estimate?.azimuthDeg
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Lectura del Azimut Estimado numérico
                    Text(
                        text = estimate?.let { String.format(Locale.US, "%.0f°", it.azimuthDeg) } ?: "--°",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00F0FF),
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "Orientación relativa al norte magnético",
                        fontSize = 11.sp,
                        color = Color(0xFF8E9AA8)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Barra e indicador de confianza
                    ConfidenceBar(
                        confidencePercent = estimate?.confidencePercent ?: 0,
                        message = estimate?.message ?: "Sin estimación"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Indicador de señal RSSI y estadísticas de la sesión
                    RssiSignalIndicator(
                        currentRssi = latestPoint?.rssi ?: -127,
                        bestRssi = estimate?.bestRssi ?: -127,
                        avgRssi = estimate?.avgRssi ?: -127f
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Instrucción para desplazarse y volver a medir
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF161C26))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "💡 Camina lentamente en la dirección estimada y vuelve a medir para mejorar la precisión.",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Botón para repetir la medición
                    Button(
                        onClick = { locatorViewModel.repeatMeasurement() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00F0FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "NUEVA MEDICIÓN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Historial de puntos acumulados en la sesión
            MeasurementHistoryList(
                history = measurementHistory,
                onClear = { locatorViewModel.clearHistory() }
            )

            // Banner técnico informativo
            TechnicalDisclaimerCard()
        }
    }
}

/**
 * Tarjeta informativa que declara transparentemente las limitaciones del sistema de medición.
 */
@Composable
private fun TechnicalDisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "ℹ️", fontSize = 16.sp)
            Text(
                text = "AP Locator proporciona una estimación de dirección basada en variaciones de RSSI y orientación del dispositivo. Los resultados pueden verse afectados por paredes, obstáculos, reflexiones multipath e interferencias.",
                fontSize = 10.sp,
                color = Color(0xFF8E9AA8),
                lineHeight = 14.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
