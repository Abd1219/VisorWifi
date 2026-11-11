package com.abdapps.visorwifi.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abdapps.visorwifi.NetworkMonitorService
import com.abdapps.visorwifi.analysis.PeriodAnalyzer
import com.abdapps.visorwifi.analysis.PeriodStatusEvaluator
import com.abdapps.visorwifi.engine.NetworkDiagnosisEngine
import com.abdapps.visorwifi.model.LatencyPoint
import com.abdapps.visorwifi.model.NetworkMetrics
import com.abdapps.visorwifi.model.ReportData
import com.abdapps.visorwifi.model.PdfGenerator
import com.abdapps.visorwifi.ui.components.DiagnosisCard
import com.abdapps.visorwifi.ui.components.LatencyCard
import com.abdapps.visorwifi.ui.components.LatencyChart
import com.abdapps.visorwifi.ui.components.RoamingEventLog
import com.abdapps.visorwifi.ui.components.WifiInfoCard
import com.abdapps.visorwifi.ui.components.RssiChart
import com.abdapps.visorwifi.ui.components.ReportInputDialog
import com.abdapps.visorwifi.ui.components.PdfPreviewDialog
import com.abdapps.visorwifi.viewmodel.NetworkDiagnosisViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTcpFallback(latencyMs: Float, target: String?): String? {
    if (target == null) return null
    return if (latencyMs >= 0f) {
        String.format(Locale.US, "TCP connect %s · %.1f ms", target, latencyMs)
    } else {
        "TCP connect $target · sin respuesta"
    }
}

/** Etiquetas de lectura rápida; no intervienen en el cálculo de las métricas. */
private fun latencyPresentationStatus(latencyMs: Float?, packetLoss: Float): String = when {
    latencyMs == null -> "Esperando medición"
    latencyMs < 0f -> "Sin respuesta ICMP"
    packetLoss > 5f -> "Pérdida detectada"
    latencyMs > 120f -> "Elevada"
    latencyMs > 50f -> "Normal"
    else -> "Estable"
}

/**
 * Pantalla principal del monitor de latencia.
 * Desarrollada de manera declarativa con Jetpack Compose.
 *
 * Administra el flujo reactivo y la interfaz de usuario en tiempo real.
 * Coordina los permisos de ubicación/notificaciones y delega en los subcomponentes
 * de interfaz desacoplados para renderizar métricas, telemetría de red y gráficos.
 *
 * @param modifier Modificador Compose de diseño.
 * @param service Instancia activa del servicio inyectado.
 * @param isServiceRunning Bandera de control de ejecución del monitoreo.
 * @param onStartService Callback para solicitar el arranque del monitoreo.
 * @param onStopService Callback para solicitar la detención del monitoreo.
 */
@Composable
fun LatencyMonitorScreen(
    modifier: Modifier = Modifier,
    service: NetworkMonitorService?,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenApLocator: () -> Unit = {}
) {
    val context = LocalContext.current
    var latestPoint by remember { mutableStateOf<LatencyPoint?>(null) }
    var historyList by remember { mutableStateOf<List<LatencyPoint>>(emptyList()) }
    var roamEvents by remember { mutableStateOf<List<String>>(emptyList()) }

    var showInputDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }

    // ViewModel del diagnóstico de red — ciclo de vida vinculado al Activity
    val diagnosisViewModel: NetworkDiagnosisViewModel = viewModel()
    val diagnosisState by diagnosisViewModel.diagnosisState.collectAsState()
    
    // Almacena dinámicamente si se cuenta con el permiso de ubicación del GPS
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    // Launcher de Compose para solicitar el permiso de Notificación en Android 13+ (Tiramisu)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onStartService()
        else Toast.makeText(context, "Se necesita permiso de notificaciones para el monitoreo continuo.", Toast.LENGTH_LONG).show()
    }

    // Launcher de Compose para solicitar permisos de ubicación física (necesario para leer SSID/BSSID reales del WiFi)
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        // Arrancar el servicio de monitoreo en cualquier caso (usa simulación o datos parciales si no se concede)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (hasNotif) onStartService()
            else notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onStartService()
        }
    }

    /**
     * Orquesta la petición secuencial de permisos antes de activar el servicio continuo.
     */
    fun startWithPermissions() {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (hasNotif) onStartService()
            else notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onStartService()
        }
    }

    // Escucha de manera reactiva y asíncrona la emisión de nuevos puntos de latencia desde el servicio.
    // CORRECCIÓN: Si el servicio se desconecta temporalmente (ej. onStop/onStart del ciclo de vida),
    // NO borramos historyList para que la gráfica mantenga los datos visibles.
    LaunchedEffect(service) {
        if (service != null) {
            // Inicializar la pantalla con los datos históricos existentes en el búfer del servicio
            val history = service.getHistoryCopy()
            // Solo reemplazamos el historial si el servicio tiene datos más completos
            if (history.isNotEmpty()) {
                historyList = history
                latestPoint = history.last()
            }
            roamEvents = service.getRoamEventsCopy()

            // Colecciona los elementos emitidos por el flujo reactivo
            service.latencyFlow.collect { point ->
                latestPoint = point
                // Actualizamos el historial manteniendo solo los últimos 600 puntos
                historyList = (historyList + point).takeLast(600)
                Log.d("LatencyMonitor", "Nuevo punto: $point, tamaño historial: ${historyList.size}")
                // Notifica al ViewModel de diagnóstico con las métricas actualizadas
                diagnosisViewModel.updateFromMetrics(historyList, point.rssi)
            }
        }
        // NOTA: No borramos datos cuando service == null (desconexión temporal del binding)
        // Los datos sólo se limpian explícitamente cuando el usuario presiona DETENER
    }

    // Escucha reactiva separada dedicada a registrar eventos de Roaming y cambio de red
    LaunchedEffect(service) {
        service?.roamingFlow?.collect { event ->
            // Mantiene en el historial visual los últimos 50 eventos capturados
            roamEvents = (roamEvents + event).takeLast(50)
        }
    }

    // Calcular pérdida de paquetes sobre ventana móvil de los últimos 100 puntos telemétricos
    val recentPoints = historyList.takeLast(100)
    val lanLoss = if (recentPoints.isNotEmpty()) {
        (recentPoints.count { it.lanLatency < 0 }.toFloat() / recentPoints.size) * 100f
    } else 0f
    val wanLoss = if (recentPoints.isNotEmpty()) {
        (recentPoints.count { it.wanLatency < 0 }.toFloat() / recentPoints.size) * 100f
    } else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Cabecera e Información de Acción ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MONITOR DE RED",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00F0FF),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Telemetría en tiempo real",
                    fontSize = 12.sp,
                    color = Color(0xFF8E9AA8)
                )
            }

            Button(
                onClick = {
                    if (historyList.size < 10) {
                        Toast.makeText(context, "Se necesitan al menos 10 muestras para generar un reporte.", Toast.LENGTH_LONG).show()
                    } else {
                        showInputDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D9488),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "REPORTE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // ── Estado del Servicio + Botón de Control de Arranque/Parada ──────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161C26))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ESTADO DEL SERVICIO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF536371),
                    letterSpacing = 1.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    // Círculo indicador de actividad con color pulsante
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (isServiceRunning) Color(0xFF00FF66)
                                else Color(0xFFFF3366)
                            )
                    )
                    Text(
                        text = if (isServiceRunning) "MONITOREANDO" else "INACTIVO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = if (isServiceRunning) "Recopilando datos de tu red" else "Inicia el monitoreo para recopilar datos",
                    fontSize = 11.sp,
                    color = Color(0xFF8E9AA8),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Botón interactivo de monitoreo
        Button(
            onClick = {
                if (isServiceRunning) {
                    onStopService()
                    historyList = emptyList()
                    Log.d("LatencyMonitor", "Service stopped, history cleared")
                } else {
                    startWithPermissions()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) Color(0xFFFF3366)
                                 else Color(0xFF00F0FF),
                contentColor = if (isServiceRunning) Color.White else Color.Black
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (isServiceRunning) "DETENER" else "MONITOREAR",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        }
        }

        // ── Resultado principal: primero explica qué ocurre ────────────────
        DiagnosisCard(state = diagnosisState)

        // ── Tarjetas de Latencia LAN y WAN Instantáneas ────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LatencyCard(
                modifier = Modifier.weight(1f),
                label = "LATENCIA LAN",
                sublabel = "Acceso local · ICMP",
                accentColor = Color(0xFF00F0FF),
                value = latestPoint?.let {
                    if (it.lanLatency >= 0) String.format(Locale.US, "%.1f ms", it.lanLatency) else "Timeout"
                } ?: "-- ms",
                packetLoss = lanLoss,
                statusText = latencyPresentationStatus(latestPoint?.lanLatency, lanLoss),
                supplementalValue = latestPoint?.let { formatTcpFallback(it.lanTcpLatency, it.lanTcpTarget) }
            )
            LatencyCard(
                modifier = Modifier.weight(1f),
                label = "LATENCIA WAN",
                sublabel = "Internet · ICMP (8.8.8.8)",
                accentColor = Color(0xFFD946EF),
                value = latestPoint?.let {
                    if (it.wanLatency >= 0) String.format(Locale.US, "%.1f ms", it.wanLatency) else "Timeout"
                } ?: "-- ms",
                packetLoss = wanLoss,
                statusText = latencyPresentationStatus(latestPoint?.wanLatency, wanLoss),
                supplementalValue = latestPoint?.let { formatTcpFallback(it.wanTcpLatency, it.wanTcpTarget) }
            )
        }
        // ── Tarjeta de Detalles e Información de Conexión WiFi ────────────
        WifiInfoCard(latestPoint = latestPoint, service = service)

        // ── Tarjeta de Historial de Intensidad de Señal RSSI ───────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF263238), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "HISTORIAL DE INTENSIDAD DE SEÑAL (RSSI)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF536371),
                    modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                )
                Text(
                    text = "Nivel de señal en dBm · últimos datos capturados",
                    fontSize = 10.sp,
                    color = Color(0xFF8E9AA8),
                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                )
                
                RssiChart(
                    history = historyList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(220.dp)
                )
            }
        }

        // ── Gráfica Dinámica en Tiempo Real (MPAndroidChart) ───────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF263238), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "HISTORIAL DE LATENCIA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF536371),
                    modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                )
                Text(
                    text = "LAN en cyan · WAN en morado · jitter en trazo tenue",
                    fontSize = 10.sp,
                    color = Color(0xFF8E9AA8),
                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                )
                
                // Enlaza la vista tradicional XML de Android en el ecosistema declarativo de Jetpack Compose
                LatencyChart(
                    history = historyList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(280.dp)
                )
            }
        }

        // ── Registro e Historial Visual de Cambios de Antena / Roaming ──────
        if (roamEvents.isNotEmpty() || isServiceRunning) {
            RoamingEventLog(roamEvents = roamEvents)
        }

        // ── Tarjeta Informativa de Procesamiento en Segundo Plano ───────────
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
                Text(text = "⚡", fontSize = 18.sp, color = Color(0xFF00F0FF))
                Text(
                    text = "El registro de latencia continúa ejecutándose en segundo plano. Puedes salir de la app o apagar la pantalla y los datos seguirán guardándose.",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF),
                    textAlign = TextAlign.Start,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Diálogos de Reporte ───────────────────────────────────────────
        if (showInputDialog) {
            ReportInputDialog(
                onDismiss = { showInputDialog = false },
                onSubmit = { technician, site, notes, signature ->
                    showInputDialog = false

                    // ── 1. Análisis estadístico del PERIODO ─────────────────────────────────
                    // Se analiza el historial COMPLETO capturado durante la sesión.
                    // diagnosisState.status NO interviene en este cálculo.
                    val periodAnalysis = PeriodAnalyzer.analyze(
                        history = historyList,
                        networkEvents = roamEvents
                    )

                    // ── 2. Evaluación del estado del PERIODO ────────────────────────────────
                    // Función determinística: misma entrada → misma salida. Sin efectos laterales.
                    val periodStatus = PeriodStatusEvaluator.evaluate(periodAnalysis)

                    // ── 3. Datos de contexto del sitio (instantáneos, solo para encabezado) ─
                    val currentPoint = latestPoint
                    val wifiBand = currentPoint?.let { p ->
                        service?.frequencyToBand(p.frequency)
                    } ?: "Desconocida"
                    val rssi    = currentPoint?.rssi ?: -127
                    val phyRate = currentPoint?.let {
                        if (it.txLinkSpeed > 0) it.txLinkSpeed else it.linkSpeed
                    } ?: 0

                    val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                    // ── 3.5. Diagnóstico heurístico sobre el periodo completo ───────────────
                    val reportMetrics = NetworkMetrics.fromPeriodAnalysis(
                        analysis = periodAnalysis,
                        phyRateMbps = phyRate,
                        latestRssi = rssi
                    )
                    val reportDiagnosis = NetworkDiagnosisEngine.evaluateDiagnosis(
                        metrics = reportMetrics,
                        lanValidSamples = historyList.count { it.lanLatency >= 0f },
                        wanValidSamples = historyList.count { it.wanLatency >= 0f }
                    )

                    // ── 4. Conclusión ejecutiva basada únicamente en métricas ya calculadas ─
                    val executiveConclusion = com.abdapps.visorwifi.model.ReportNarrativeGenerator
                        .executiveConclusion(periodAnalysis, periodStatus)

                    // ── 5. Construcción de ReportData ───────────────────────────────────────
                    // periodStatus y periodAnalysis son la fuente de verdad del PDF.
                    // reportDiagnosis (título, confianza, evidencias, recomendación) se calculó
                    // a partir del historial completo de la sesión para evitar sesgo de tiempo real.
                    val reportData = ReportData(
                        dateTime           = formattedDate,
                        technicianName     = technician,
                        siteName           = site,
                        periodStatus       = periodStatus,
                        periodAnalysis     = periodAnalysis,
                        diagnosisTitle     = reportDiagnosis.title,
                        confidence         = reportDiagnosis.confidence,
                        evidences          = reportDiagnosis.evidences,
                        recommendation     = reportDiagnosis.recommendation,
                        executiveConclusion = executiveConclusion,
                        lanLatencyAvgMs    = periodAnalysis.avgLanLatencyMs,
                        wanLatencyAvgMs    = periodAnalysis.avgWanLatencyMs,
                        wifiBand           = wifiBand,
                        rssiDbm            = rssi,
                        phyRateMbps        = phyRate,
                        latencyHistory     = historyList,
                        networkEvents      = roamEvents,
                        technicianNotes    = notes,
                        technicianSignature = signature
                    )

                    try {
                        val file = PdfGenerator.generateReport(context, reportData)
                        generatedFile = file
                        showPreviewDialog = true
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al generar PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        if (showPreviewDialog && generatedFile != null) {
            PdfPreviewDialog(
                file = generatedFile!!,
                onDismiss = {
                    showPreviewDialog = false
                    generatedFile = null
                }
            )
        }
    }
}
