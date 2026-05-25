package com.abdapps.visorwifi

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.abdapps.visorwifi.ui.theme.VisorWifiTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.flow.collect
import java.util.Locale

/**
 * Actividad Principal de la aplicación VisorWifi.
 *
 * Actúa como punto de entrada de la interfaz de usuario. Administra:
 * 1. El ciclo de vida de conexión con el servicio en segundo plano ([NetworkMonitorService]).
 * 2. La solicitud asíncrona de permisos críticos requeridos por el sistema (Ubicación y Notificaciones).
 * 3. La renderización reactiva de los datos telemétricos usando Jetpack Compose y MPAndroidChart.
 */
class MainActivity : ComponentActivity() {

    /**
     * Referencia directa del servicio de monitoreo en ejecución para invocar sus métodos expuestos.
     */
    private var networkService: NetworkMonitorService? = null

    /**
     * Estado observable de Compose que aloja la referencia del servicio,
     * permitiendo que los componentes Compose se recompongan automáticamente cuando cambie de estado.
     */
    private val serviceInstance = mutableStateOf<NetworkMonitorService?>(null)

    /**
     * Estado observable que indica si la actividad está enlazada actualmente al servicio.
     */
    private val isServiceBound = mutableStateOf(false)

    /**
     * Estado observable que define si el servicio está actualmente activo en segundo plano.
     */
    private val isServiceRunningState = mutableStateOf(false)

    /**
     * Interfaz de comunicación con el servicio que gestiona los eventos de enlace (Binding).
     */
    private val connection = object : ServiceConnection {
        /**
         * Se ejecuta al establecer exitosamente la comunicación con el servicio.
         */
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? NetworkMonitorService.LocalBinder
            networkService = localBinder?.getService()
            serviceInstance.value = networkService
            isServiceBound.value = true
            isServiceRunningState.value = true
        }

        /**
         * Se ejecuta de forma inesperada si el servicio muere (por ejemplo, por falta de memoria).
         */
        override fun onServiceDisconnected(name: ComponentName?) {
            networkService = null
            serviceInstance.value = null
            isServiceBound.value = false
            isServiceRunningState.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Habilita el renderizado de borde a borde (Edge-to-Edge)
        enableEdgeToEdge()

        // Si la app se abre y el servicio ya está corriendo, realiza el binding inmediatamente.
        if (isServiceRunning(this, NetworkMonitorService::class.java)) {
            bindToService()
        }

        setContent {
            VisorWifiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = androidx.compose.ui.graphics.Color(0xFF0B0E14)
                ) { innerPadding ->
                    // Pantalla de monitoreo principal conectada a los flujos del servicio.
                    LatencyMonitorScreen(
                        modifier = Modifier.padding(innerPadding),
                        service = serviceInstance.value,
                        isServiceRunning = isServiceRunningState.value,
                        onStartService = { startMonitorService() },
                        onStopService = { stopMonitorService() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Garantizar el enlace al servicio al volver a poner la Actividad en primer plano
        if (isServiceRunning(this, NetworkMonitorService::class.java)) {
            bindToService()
        }
    }

    override fun onStop() {
        super.onStop()
        // Romper el enlace al salir de la pantalla para evitar fugas de memoria (Memory Leaks).
        // El servicio persistirá corriendo en segundo plano porque es un Foreground Service.
        unbindFromService()
    }

    /**
     * Lanza e inicia formalmente el servicio en primer plano e interactúa con él.
     */
    private fun startMonitorService() {
        val intent = Intent(this, NetworkMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindToService()
    }

    /**
     * Detiene y cancela la ejecución del servicio en segundo plano.
     */
    private fun stopMonitorService() {
        unbindFromService()
        val intent = Intent(this, NetworkMonitorService::class.java)
        stopService(intent)
        isServiceRunningState.value = false
    }

    /**
     * Solicita enlazarse al servicio de monitoreo de manera asíncrona.
     */
    private fun bindToService() {
        if (!isServiceBound.value) {
            val intent = Intent(this, NetworkMonitorService::class.java)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Cancela la vinculación activa al servicio de monitoreo.
     */
    private fun unbindFromService() {
        if (isServiceBound.value) {
            unbindService(connection)
            isServiceBound.value = false
            serviceInstance.value = null
            networkService = null
        }
    }

    /**
     * Verifica si una clase de servicio específica se encuentra actualmente activa en el sistema.
     * Método heredado para asegurar la sincronización de estado de la UI.
     *
     * @param context Contexto de la aplicación.
     * @param serviceClass Clase del servicio que se desea consultar.
     * @return True si el servicio está activo, de lo contrario False.
     */
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }
}

/**
 * Pantalla principal del monitor de latencia.
 * Desarrollada de manera declarativa con Jetpack Compose.
 *
 * Administra el flujo reactivo y la interfaz de usuario en tiempo real.
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
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    var latestPoint by remember { mutableStateOf<LatencyPoint?>(null) }
    var chartRef by remember { mutableStateOf<LineChart?>(null) }
    var roamEvents by remember { mutableStateOf<List<String>>(emptyList()) }
    
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

    // Escucha de manera reactiva y asíncrona la emisión de nuevos puntos de latencia desde el servicio
    LaunchedEffect(service) {
        if (service != null) {
            // Inicializar la pantalla con los datos históricos existentes en el búfer del servicio
            val history = service.getHistoryCopy()
            if (history.isNotEmpty()) latestPoint = history.last()
            roamEvents = service.getRoamEventsCopy()

            // Colecciona los elementos emitidos por el flujo reactivo
            service.latencyFlow.collect { point ->
                latestPoint = point
                val chart = chartRef
                if (chart != null) {
                    // Adiciona el nuevo punto a la gráfica interactiva en tiempo real
                    addPointToChart(chart, point)
                }
            }
        } else {
            latestPoint = null
        }
    }

    // Escucha reactiva separada dedicada a registrar eventos de Roaming y cambio de red
    LaunchedEffect(service) {
        service?.roamingFlow?.collect { event ->
            // Mantiene en el historial visual los últimos 50 eventos capturados
            roamEvents = (roamEvents + event).takeLast(50)
        }
    }

    // Inicializa o repuebla la gráfica cuando se asocia el servicio o cambia la referencia de la vista de la gráfica
    LaunchedEffect(service, chartRef) {
        val activeService = service
        val activeChart = chartRef
        if (activeService != null && activeChart != null) {
            val history = activeService.getHistoryCopy()
            populateChartWithHistory(activeChart, history)
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
        // ── Cabecera Premium Neo-Brutalista ────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "VISOR WIFI",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = androidx.compose.ui.graphics.Color(0xFF00F0FF),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "Monitor de Red en Tiempo Real",
                fontSize = 14.sp,
                color = androidx.compose.ui.graphics.Color(0xFF8E9AA8)
            )
        }

        // ── Estado del Servicio + Botón de Control de Arranque/Parada ──────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF161C26))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ESTADO DEL SERVICIO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF536371),
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
                                if (isServiceRunning) androidx.compose.ui.graphics.Color(0xFF00FF66)
                                else androidx.compose.ui.graphics.Color(0xFFFF3366)
                            )
                    )
                    Text(
                        text = if (isServiceRunning) "MONITOREANDO" else "INACTIVO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            }

            // Botón interactivo de monitoreo
            Button(
                onClick = {
                    if (isServiceRunning) {
                        onStopService()
                        chartRef?.clear()
                        chartRef?.invalidate()
                    } else {
                        startWithPermissions()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) androidx.compose.ui.graphics.Color(0xFFFF3366)
                                     else androidx.compose.ui.graphics.Color(0xFF00F0FF),
                    contentColor = androidx.compose.ui.graphics.Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isServiceRunning) "DETENER" else "MONITOREAR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // ── Tarjetas de Latencia LAN y WAN Instantáneas ────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LatencyCard(
                modifier = Modifier.weight(1f),
                label = "LATENCIA LAN",
                sublabel = "Acceso Local",
                accentColor = androidx.compose.ui.graphics.Color(0xFF00F0FF),
                value = latestPoint?.let {
                    if (it.lanLatency >= 0) String.format(Locale.US, "%.1f ms", it.lanLatency) else "Timeout"
                } ?: "-- ms"
            )
            LatencyCard(
                modifier = Modifier.weight(1f),
                label = "LATENCIA WAN",
                sublabel = "Internet (8.8.8.8)",
                accentColor = androidx.compose.ui.graphics.Color(0xFFD946EF),
                value = latestPoint?.let {
                    if (it.wanLatency >= 0) String.format(Locale.US, "%.1f ms", it.wanLatency) else "Timeout"
                } ?: "-- ms"
            )
        }

        // ── Tarjeta de Detalles e Información de Conexión WiFi ────────────
        WifiInfoCard(latestPoint = latestPoint, service = service)

        // ── Gráfica Dinámica en Tiempo Real (MPAndroidChart) ───────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, androidx.compose.ui.graphics.Color(0xFF263238), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF0F141C))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "HISTORIAL DE LATENCIA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF536371),
                    modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                )
                
                // Enlaza la vista tradicional XML de Android en el ecosistema declarativo de Jetpack Compose
                AndroidView(
                    factory = { ctx ->
                        LineChart(ctx).apply {
                            setupChartStyling(this)
                            // Inicializar datasets vacíos para evitar layouts sin pintar (No Data state)
                            val lanDataSet = createDataSet(ArrayList(), "LAN (Acceso Local)", "#00F0FF")
                            val wanDataSet = createDataSet(ArrayList(), "WAN (Internet)", "#D946EF")
                            data = LineData(lanDataSet, wanDataSet)
                            invalidate()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(280.dp),
                    update = { chart ->
                        // Guardar la referencia estática de la vista para poder alterarla externamente
                        if (chartRef !== chart) {
                            chartRef = chart
                        }
                    }
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
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF111827))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "⚡", fontSize = 18.sp, color = androidx.compose.ui.graphics.Color(0xFF00F0FF))
                Text(
                    text = "El registro de latencia continúa ejecutándose en segundo plano. Puedes salir de la app o apagar la pantalla y los datos seguirán guardándose.",
                    fontSize = 11.sp,
                    color = androidx.compose.ui.graphics.Color(0xFF9CA3AF),
                    textAlign = TextAlign.Start,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Componente Compose para renderizar métricas individuales de latencia en un formato estético premium.
 */
@Composable
private fun LatencyCard(
    modifier: Modifier = Modifier,
    label: String,
    sublabel: String,
    accentColor: androidx.compose.ui.graphics.Color,
    value: String
) {
    Card(
        modifier = modifier.border(
            1.dp,
            Brush.linearGradient(colors = listOf(accentColor.copy(alpha = 0.12f), accentColor.copy(alpha = 0.02f))),
            RoundedCornerShape(16.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF121620))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Box(
                modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accentColor)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF8E9AA8))
            Text(text = sublabel, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF536371))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = androidx.compose.ui.graphics.Color.White,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Componente Compose para visualizar detalles técnicos del enlace WiFi activo:
 * SSID de red, dirección BSSID del Access Point, Banda de Frecuencia física,
 * fuerza del RSSI calificado y tasas físicas de transmisión (PHY TX/RX).
 */
@Composable
private fun WifiInfoCard(latestPoint: LatencyPoint?, service: NetworkMonitorService?) {
    val band = latestPoint?.let {
        if (it.frequency > 0) service?.frequencyToBand(it.frequency) ?: "" else ""
    } ?: ""
    val rssi = latestPoint?.rssi ?: -127
    val rssiQuality = if (rssi > -127) service?.rssiToQuality(rssi) ?: "--" else "--"
    val rssiText = if (rssi > -127) "$rssi dBm" else "--"
    val txSpeed = latestPoint?.txLinkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" } ?: "--"
    val rxSpeed = latestPoint?.rxLinkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" } ?: "--"
    val ssidText = latestPoint?.ssid?.takeIf { it.isNotBlank() } ?: "Sin permiso de ubicación"
    val bssidText = latestPoint?.bssid?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" } ?: "--"

    // Selección dinámica de color en base al nivel cualitativo de la señal
    val rssiColor = when (rssiQuality) {
        "Excelente" -> androidx.compose.ui.graphics.Color(0xFF00FF66)
        "Muy Buena" -> androidx.compose.ui.graphics.Color(0xFF00F0FF)
        "Buena" -> androidx.compose.ui.graphics.Color(0xFFFFD700)
        "Regular" -> androidx.compose.ui.graphics.Color(0xFFFF9900)
        "Débil" -> androidx.compose.ui.graphics.Color(0xFFFF3366)
        else -> androidx.compose.ui.graphics.Color(0xFF536371)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0x2000FF66),
                        androidx.compose.ui.graphics.Color(0x0500FF66)
                    )
                ),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF121620))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CONEXIÓN WiFi",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF536371),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Red (SSID) y MAC del AP de destino (BSSID)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    WifiInfoRow(icon = "📶", label = "RED (SSID)", value = ssidText, valueColor = androidx.compose.ui.graphics.Color(0xFF00F0FF))
                    Spacer(modifier = Modifier.height(8.dp))
                    WifiInfoRow(icon = "🔗", label = "AP (BSSID)", value = bssidText, valueColor = androidx.compose.ui.graphics.Color(0xFF8E9AA8))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFF1C2331))
            Spacer(modifier = Modifier.height(12.dp))

            // Fila de parámetros físicos: Banda, Calidad y Velocidad del Enlace físico
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Frecuencia física
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "BANDA", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = band.ifEmpty { "--" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = androidx.compose.ui.graphics.Color(0xFF00F0FF),
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                // Nivel cualitativo y cuantitativo del RSSI
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "SEÑAL", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rssiText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = rssiColor,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(text = rssiQuality, fontSize = 10.sp, color = rssiColor.copy(alpha = 0.7f))
                }
                
                // Velocidad PHY de subida y bajada en tiempo real
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "VELOCIDAD PHY", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF536371), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "↑", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF00FF66))
                        Text(text = txSpeed, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "↓", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF00F0FF))
                        Text(text = rxSpeed, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

/**
 * Celda básica para renderizar pares clave-valor dentro de la tarjeta de WiFi.
 */
@Composable
private fun WifiInfoRow(
    icon: String,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White
) {
    Column {
        Text(text = label, fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF536371), fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = icon, fontSize = 12.sp)
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * Caja de registro (Log) que expone los cambios de antena/itinerancia
 * de manera ordenada, mostrando los eventos más recientes en la parte superior.
 */
@Composable
private fun RoamingEventLog(roamEvents: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, androidx.compose.ui.graphics.Color(0xFF1C2331), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF0A0F18))
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
                    color = androidx.compose.ui.graphics.Color(0xFF536371),
                    letterSpacing = 1.sp
                )
                // Contador totalizador de eventos de red capturados
                if (roamEvents.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF1C2331))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${roamEvents.size} evento${if (roamEvents.size != 1) "s" else ""}",
                            fontSize = 10.sp,
                            color = androidx.compose.ui.graphics.Color(0xFFFFD700),
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
                    color = androidx.compose.ui.graphics.Color(0xFF536371),
                    fontFamily = FontFamily.Monospace
                )
            } else {
                // Desplegar un máximo de 10 eventos en orden inverso (del más reciente al más antiguo)
                roamEvents.reversed().take(10).forEach { event ->
                    val isRoaming = event.contains("Roaming")
                    val eventColor = if (isRoaming)
                        androidx.compose.ui.graphics.Color(0xFFFFD700) // Amarillo para cambio de antena AP
                    else
                        androidx.compose.ui.graphics.Color(0xFF00F0FF) // Cian para cambio de red SSID
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

/**
 * Define y personaliza el estilo gráfico, interacciones y rejillas del componente LineChart.
 * Configura los ejes, la posición de las leyendas y los formateadores de valores.
 *
 * @param chart Instancia de LineChart (MPAndroidChart) a estilizar.
 */
private fun setupChartStyling(chart: LineChart) {
    chart.apply {
        description.isEnabled = false
        setTouchEnabled(true)
        isDragEnabled = true
        isScaleXEnabled = true
        isScaleYEnabled = false // Bloqueado el zoom vertical para mantener consistencia de lectura
        setPinchZoom(true)
        setDrawGridBackground(false)
        setBackgroundColor(Color.TRANSPARENT)
        setNoDataText("Inicia el monitoreo para visualizar la gráfica")
        setNoDataTextColor(Color.parseColor("#536371"))

        // Estilización de la leyenda informativa inferior
        legend.apply {
            isEnabled = true
            textColor = Color.parseColor("#8E9AA8")
            textSize = 10f
            form = Legend.LegendForm.LINE
            formSize = 8f
            formLineWidth = 2f
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            xEntrySpace = 16f
        }

        // Estilización del Eje X (Línea de tiempo en segundos transcurridos)
        xAxis.apply {
            textColor = Color.parseColor("#536371")
            textSize = 9f
            gridColor = Color.parseColor("#1C2331")
            gridLineWidth = 0.8f
            enableGridDashedLine(10f, 10f, 0f)
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            setDrawAxisLine(true)
            axisLineColor = Color.parseColor("#1C2331")
            // Agrega el sufijo "s" (segundos) a los valores del eje temporal
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}s"
            }
        }

        // Estilización del Eje Y Izquierdo (Latencia en milisegundos)
        axisLeft.apply {
            textColor = Color.parseColor("#536371")
            textSize = 9f
            gridColor = Color.parseColor("#1C2331")
            gridLineWidth = 0.8f
            enableGridDashedLine(10f, 10f, 0f)
            setDrawGridLines(true)
            setDrawAxisLine(true)
            axisLineColor = Color.parseColor("#1C2331")
            axisMinimum = 0f // El valor mínimo no puede ser negativo
            spaceTop = 15f
            // Agrega el sufijo " ms" a los valores de latencia
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()} ms"
            }
        }

        // Deshabilitar el eje derecho redundante para limpiar el área útil del gráfico
        axisRight.isEnabled = false
    }
}

/**
 * Carga todo el historial almacenado en el búfer del servicio directamente a la gráfica.
 * Se ejecuta al iniciar la aplicación o al reconectarse al servicio activo.
 *
 * @param chart Instancia de la gráfica.
 * @param history Colección de puntos de medición previos.
 */
private fun populateChartWithHistory(chart: LineChart, history: List<LatencyPoint>) {
    val lanEntries = ArrayList<Entry>()
    val wanEntries = ArrayList<Entry>()

    history.forEachIndexed { index, point ->
        val x = index.toFloat()
        // Filtrar mediciones con error/timeouts (-1f) para evitar caídas a cero erróneas en la visualización
        if (point.lanLatency >= 0) lanEntries.add(Entry(x, point.lanLatency))
        if (point.wanLatency >= 0) wanEntries.add(Entry(x, point.wanLatency))
    }

    val lanDataSet = createDataSet(lanEntries, "LAN (Acceso Local)", "#00F0FF")
    val wanDataSet = createDataSet(wanEntries, "WAN (Internet)", "#D946EF")

    val lineData = LineData(lanDataSet, wanDataSet)
    chart.data = lineData
    chart.notifyDataSetChanged()

    // Ajusta la ventana visual a los últimos 60 segundos si el historial supera dicho umbral
    if (history.size > 60) {
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX((history.size - 1).toFloat())
    } else {
        chart.setVisibleXRangeMaximum(60f)
    }

    chart.invalidate()
}

/**
 * Fábrica de conjuntos de datos lineales personalizados (LineDataSet) con sombreado degradado
 * y suavizado por curvas de Bézier cúbicas de alta fidelidad.
 *
 * @param entries Lista de coordenadas X e Y correspondientes al dataset.
 * @param label Título o descriptor del dataset.
 * @param colorHex Representación en formato hexadecimal del color de la línea.
 * @return Estructura optimizada [LineDataSet].
 */
private fun createDataSet(entries: ArrayList<Entry>, label: String, colorHex: String): LineDataSet {
    return LineDataSet(entries, label).apply {
        color = Color.parseColor(colorHex)
        lineWidth = 2.2f
        setDrawCircles(false) // Deshabilita los puntos marcadores para mejorar el rendimiento
        setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER // Curvas suaves
        cubicIntensity = 0.12f
        setDrawValues(false)
        setDrawFilled(true) // Área de sombra bajo la curva
        fillColor = Color.parseColor(colorHex)
        fillAlpha = 15 // Transparencia sutil de 15/255
    }
}

/**
 * Añade un nuevo punto telemétrico de forma segura en tiempo real en la gráfica interactiva.
 *
 * CORRECCIÓN CRÍTICA DE INTEGRIDAD:
 * Para prevenir el bloqueo o cuelgue inesperado en el dibujado de MPAndroidChart provocado por
 * entradas desordenadas en el Eje X (lo cual ocurre si una corrutina se suspende o si se registran
 * timeouts), calculamos la coordenada X de manera estrictamente creciente: `maxOf(maxLanX, maxWanX) + 1f`.
 * Esto garantiza que, incluso si no se insertó una muestra anterior (ej: LAN fallida y WAN exitosa),
 * el índice de abscisas X se mantenga en orden estrictamente ascendente como exige la librería.
 *
 * @param chart Instancia de la gráfica.
 * @param point Nuevo punto de latencia registrado.
 */
private fun addPointToChart(chart: LineChart, point: LatencyPoint) {
    var data = chart.data

    if (data == null) {
        val lanDataSet = createDataSet(ArrayList(), "LAN (Acceso Local)", "#00F0FF")
        val wanDataSet = createDataSet(ArrayList(), "WAN (Internet)", "#D946EF")
        data = LineData(lanDataSet, wanDataSet)
        chart.data = data
    }

    val lanSet = data.getDataSetByIndex(0) as? LineDataSet
    val wanSet = data.getDataSetByIndex(1) as? LineDataSet

    if (lanSet != null && wanSet != null) {
        // Resolver de forma segura la mayor coordenada X registrada hasta el momento
        val maxLanX = if (lanSet.entryCount > 0) lanSet.getEntryForIndex(lanSet.entryCount - 1).x else -1f
        val maxWanX = if (wanSet.entryCount > 0) wanSet.getEntryForIndex(wanSet.entryCount - 1).x else -1f
        val nextX = maxOf(maxLanX, maxWanX) + 1f

        // Adicionar únicamente si representa una latencia válida no fallida
        if (point.lanLatency >= 0) data.addEntry(Entry(nextX, point.lanLatency), 0)
        if (point.wanLatency >= 0) data.addEntry(Entry(nextX, point.wanLatency), 1)

        // Indicarle a la gráfica que su estructura de datos interna se actualizó
        data.notifyDataChanged()
        chart.notifyDataSetChanged()
        
        // Mantener el scroll dinámico acotado a los últimos 60 segundos
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX(nextX)
        chart.invalidate()
    }
}