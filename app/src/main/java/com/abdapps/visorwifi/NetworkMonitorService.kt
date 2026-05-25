package com.abdapps.visorwifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * Servicio en primer plano (Foreground Service) encargado de monitorear el rendimiento de la red.
 * Realiza mediciones de latencia periódicas cada segundo hacia la puerta de enlace local (LAN)
 * y hacia un servidor externo en Internet (WAN), además de detectar eventos de itinerancia (roaming)
 * y cambios de red inalámbrica en tiempo real.
 *
 * Utiliza Corrutinas de Kotlin y flujos compartidos reactivos (SharedFlow) para enviar
 * actualizaciones inmediatas a la interfaz de usuario en segundo plano de manera eficiente.
 */
class NetworkMonitorService : Service() {

    /**
     * Enlazador (Binder) local para permitir que las actividades clientes interactúen directamente
     * con los métodos y propiedades expuestas de esta instancia de servicio.
     */
    private val binder = LocalBinder()

    /**
     * Contexto de ejecución de corrutinas (CoroutineScope) para tareas del servicio.
     * Se usa [SupervisorJob] de forma que el fallo en una tarea interna (como un ping fallido)
     * no cancele todo el scope del servicio, garantizando la resiliencia del monitoreo.
     * NOTA: No se cancela al detener el servicio; en su lugar cancelamos sólo el [job] de monitoreo
     * activo para permitir reinicios sin necesidad de recrear el ServiceScope.
     */
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Trabajo de corrutina activo que ejecuta el ciclo infinito de monitoreo.
     */
    private var job: Job? = null

    /**
     * Búfer circular en memoria para almacenar el historial de mediciones de latencia.
     * Protegido por sincronización explícita para evitar condiciones de carrera (thread-safety).
     */
    private val history = mutableListOf<LatencyPoint>()

    /**
     * Límite de capacidad del búfer de historial en memoria.
     * Con una frecuencia de muestreo de 1 pt/seg, 600 entradas corresponden a 10 minutos de datos históricos.
     */
    private val maxHistorySize = 600

    /**
     * Registro en memoria de eventos históricos de roaming o cambios de SSID.
     */
    private val roamEvents = mutableListOf<String>()

    /**
     * Caché del BSSID (MAC del AP) detectado en la última muestra, para identificar eventos de roaming.
     */
    private var lastBssid: String? = null

    /**
     * Caché del SSID (Nombre de red) detectado en la última muestra, para identificar cambios de red.
     */
    private var lastSsid: String? = null

    /**
     * Flujo interno de emisión de puntos de latencia capturados en tiempo real.
     * Posee una capacidad extra en búfer para soportar picos y evitar suspender al emisor.
     */
    private val _latencyFlow = MutableSharedFlow<LatencyPoint>(replay = 0, extraBufferCapacity = 64)

    /**
     * Flujo público de lectura de latencia expuesto de forma reactiva (solo lectura).
     */
    val latencyFlow = _latencyFlow.asSharedFlow()

    /**
     * Flujo interno para notificar en tiempo real eventos de roaming y cambio de red.
     */
    private val _roamingFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 32)

    /**
     * Flujo público de lectura de eventos de red expuesto de forma reactiva (solo lectura).
     */
    val roamingFlow = _roamingFlow.asSharedFlow()

    private val random = Random()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    /**
     * Binder local personalizado para permitir el acceso directo a la instancia del servicio.
     */
    inner class LocalBinder : Binder() {
        /**
         * Retorna la instancia de [NetworkMonitorService] en ejecución.
         */
        fun getService(): NetworkMonitorService = this@NetworkMonitorService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        // Crear el canal de notificación requerido para los servicios de primer plano a partir de Android O.
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Maneja la acción explícita para detener el servicio enviado desde la notificación en segundo plano.
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        // Inicia el servicio en primer plano mostrando una notificación continua al usuario.
        startForeground(NOTIFICATION_ID, buildNotification("Monitoreando red...", "Obteniendo datos de latencia..."))

        // Evita duplicar el bucle de monitoreo si ya se encuentra activo.
        if (job == null || job?.isActive == false) {
            startMonitoringLoop()
        }

        // Si el sistema destruye el servicio por falta de memoria, se solicita recrearlo con un intent nulo.
        return START_STICKY
    }

    /**
     * Inicia la ejecución periódica del bucle de monitoreo de red en segundo plano.
     * Utiliza un hilo de despacho optimizado para operaciones de E/S y concurrencia.
     */
    private fun startMonitoringLoop() {
        job = serviceScope.launch {
            while (isActive) {
                // 1. Obtener datos telemétricos del adaptador WiFi
                val wifiInfo = getWifiInfo()
                // 2. Resolver dirección IP de la puerta de enlace local
                val gatewayIp = getGatewayIpAddress()

                // 3. Evaluar e identificar eventos de roaming o cambio de red antes de tomar la muestra
                checkForRoaming(wifiInfo)

                // 4. Medir latencias de manera concurrente para evitar bloqueos secuenciales
                val lanDeferred = async(Dispatchers.IO) {
                    var result = executePing(gatewayIp)
                    // Fallback 1: Si falla ICMP (ping ordinario), intentar establecer conexión TCP en puertos comunes
                    if (result < 0) {
                        result = measureTcpLatency(gatewayIp, 53) // DNS
                        if (result < 0) {
                            result = measureTcpLatency(gatewayIp, 80) // HTTP
                        }
                    }
                    result
                }

                val wanDeferred = async(Dispatchers.IO) {
                    var result = executePing("8.8.8.8")
                    // Fallback 1: Si falla ping a DNS de Google, intentar por socket TCP
                    if (result < 0) {
                        result = measureTcpLatency("8.8.8.8", 53)
                        if (result < 0) {
                            // Fallback 2: Intentar conexión a DNS de Cloudflare en caso de que Google no responda
                            result = measureTcpLatency("1.1.1.1", 53)
                        }
                    }
                    result
                }

                // Esperar a que ambas mediciones concurrentes terminen
                val lanResult = lanDeferred.await()
                val wanResult = wanDeferred.await()

                // 5. Reportar mediciones reales directamente. Si fallan (valor -1f), se reportan como tal para reflejar la realidad.
                val lanLatency = lanResult
                val wanLatency = wanResult

                // 6. Construir el nuevo punto de latencia telemétrico
                val point = LatencyPoint(
                    timestamp = System.currentTimeMillis(),
                    lanLatency = lanLatency,
                    wanLatency = wanLatency,
                    ssid = wifiInfo?.ssid?.removeSurrounding("\""),
                    bssid = wifiInfo?.bssid,
                    rssi = wifiInfo?.rssi ?: -127,
                    frequency = wifiInfo?.frequency ?: -1,
                    linkSpeed = wifiInfo?.linkSpeed ?: -1,
                    txLinkSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        wifiInfo?.txLinkSpeedMbps ?: -1 else wifiInfo?.linkSpeed ?: -1,
                    rxLinkSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        wifiInfo?.rxLinkSpeedMbps ?: -1 else wifiInfo?.linkSpeed ?: -1
                )

                // 7. Insertar de manera segura en el historial acotado (Búfer circular)
                synchronized(history) {
                    history.add(point)
                    if (history.size > maxHistorySize) {
                        history.removeAt(0)
                    }
                }

                // 8. Emitir el nuevo punto telemétrico de manera asíncrona a los observadores (UI)
                _latencyFlow.emit(point)

                // 9. Actualizar dinámicamente la notificación del sistema con lecturas actuales
                val band = frequencyToBand(wifiInfo?.frequency ?: -1)
                val rssiText = if ((wifiInfo?.rssi ?: -127) != -127) " | ${wifiInfo?.rssi} dBm" else ""
                updateNotification(
                    "Monitoreo de Red Activo",
                    String.format(
                        Locale.US,
                        "LAN: %.1f ms | WAN: %.1f ms%s%s",
                        lanLatency,
                        wanLatency,
                        if (band.isNotEmpty()) " | $band" else "",
                        rssiText
                    )
                )

                // Intervalo de muestreo: 1 segundo
                delay(1000)
            }
        }
    }

    /**
     * Compara el BSSID y el SSID actuales con los registrados en la iteración previa
     * para identificar e informar transiciones de antena o cambios de red.
     *
     * @param wifiInfo Estructura con la información de enlace inalámbrico actual.
     */
    private suspend fun checkForRoaming(wifiInfo: WifiInfo?) {
        val currentBssid = wifiInfo?.bssid
        val currentSsid = wifiInfo?.ssid?.removeSurrounding("\"")
        val currentRssi = wifiInfo?.rssi ?: -127
        val timestamp = timeFormat.format(Date())

        // Ignorar la dirección BSSID por defecto o nula (mientras se activa el adaptador)
        if (currentBssid != null && currentBssid != "02:00:00:00:00:00") {
            
            // Caso A: Cambio de Red completo (el nombre de la red SSID cambió)
            if (lastSsid != null && currentSsid != null && lastSsid != currentSsid) {
                val event = "[$timestamp] 🔀 Cambio de RED: \"$lastSsid\" ➔ \"$currentSsid\" (RSSI: $currentRssi dBm)"
                recordRoamEvent(event)
            }
            // Caso B: Roaming Inalámbrico (Mismo SSID, pero cambió la dirección MAC del AP de destino BSSID)
            else if (lastBssid != null && lastBssid != currentBssid && currentSsid == lastSsid) {
                val event = "[$timestamp] 📡 Roaming: $lastBssid ➔ $currentBssid (RSSI: $currentRssi dBm)"
                recordRoamEvent(event)
            }

            // Actualizar variables de estado previas
            lastBssid = currentBssid
            lastSsid = currentSsid
        }
    }

    /**
     * Registra un evento de red en el historial y lo emite asíncronamente al flujo.
     * Sincronizado para asegurar la integridad de la lista compartida.
     *
     * @param event Mensaje de texto descriptivo del cambio de red.
     */
    private suspend fun recordRoamEvent(event: String) {
        synchronized(roamEvents) {
            roamEvents.add(event)
            if (roamEvents.size > 100) roamEvents.removeAt(0)
        }
        _roamingFlow.emit(event)
    }

    /**
     * Recupera la información del adaptador inalámbrico del sistema.
     *
     * @return Objeto [WifiInfo] que contiene parámetros de conexión del WiFi, o nulo
     * si el servicio de WiFi no está disponible o el adaptador está apagado.
     */
    @Suppress("DEPRECATION")
    private fun getWifiInfo(): WifiInfo? {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.connectionInfo
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convierte la frecuencia física del canal de radio (MHz) a una banda comercial descriptiva.
     *
     * @param frequencyMHz Frecuencia actual del enlace en Megahercios.
     * @return Nombre de la banda ("2.4 GHz", "5 GHz", "6 GHz") o una cadena vacía si no es reconocible.
     */
    fun frequencyToBand(frequencyMHz: Int): String {
        return when {
            frequencyMHz in 2400..2500 -> "2.4 GHz"
            frequencyMHz in 5150..5850 -> "5 GHz"
            frequencyMHz >= 5945 -> "6 GHz"
            else -> ""
        }
    }

    /**
     * Clasifica de manera cualitativa el nivel de señal inalámbrica (RSSI en dBm)
     * para facilitar la visualización del estado del enlace.
     *
     * @param rssi Intensidad de la señal recibida en decibelios-miliwatio.
     * @return Nivel de calidad ("Excelente", "Muy Buena", "Buena", "Regular", "Débil" o "--").
     */
    fun rssiToQuality(rssi: Int): String {
        return when {
            rssi >= -50 -> "Excelente"
            rssi >= -60 -> "Muy Buena"
            rssi >= -70 -> "Buena"
            rssi >= -80 -> "Regular"
            rssi > -127 -> "Débil"
            else -> "--"
        }
    }

    /**
     * Resuelve y retorna la dirección IP de la puerta de enlace (Gateway) local.
     * Analiza los parámetros de red provistos por el servidor DHCP del Access Point.
     *
     * @return Cadena con la dirección IPv4 formateada (ej. "192.168.1.1").
     */
    private fun getGatewayIpAddress(): String {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wm?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val gateway = dhcp.gateway
                // Conversión de entero Little-Endian a formato decimal punteado IPv4
                String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    gateway and 0xff,
                    gateway shr 8 and 0xff,
                    gateway shr 16 and 0xff,
                    gateway shr 24 and 0xff
                )
            } else {
                "192.168.1.1"
            }
        } catch (e: Exception) {
            "192.168.1.1"
        }
    }

    /**
     * Ejecuta una petición ICMP Echo Request directa (comando Ping) a nivel de sistema.
     * Abre un subproceso de shell nativo de Android.
     *
     * @param ip Dirección del host de destino.
     * @return Tiempo RTT medido en milisegundos, o -1f si la petición expira o el comando falla.
     */
    private fun executePing(ip: String): Float {
        try {
            // Invoca el ejecutable del sistema 'ping' de Android enviando 1 paquete con timeout de 1 segundo
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 1 $ip")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var timeMs = -1f
            // Analiza línea por línea el flujo de salida buscando la subcadena "time="
            while (reader.readLine().also { line = it } != null) {
                if (line != null && line!!.contains("time=")) {
                    val index = line!!.indexOf("time=")
                    if (index != -1) {
                        val afterTime = line!!.substring(index + 5).trim()
                        val timeStr = afterTime.split(" ")[0].replace("ms", "").trim()
                        timeMs = timeStr.toFloatOrNull() ?: -1f
                    }
                }
            }
            process.waitFor()
            return timeMs
        } catch (e: Exception) {
            return -1f
        }
    }

    /**
     * Método alternativo de medición de RTT (Fallback) estableciendo un Socket TCP contra un puerto específico.
     * Se usa en escenarios donde el Gateway o los firewalls de red descartan paquetes ICMP (Ping silencioso).
     *
     * @param ip Dirección del host de destino.
     * @param port Puerto de destino (comúnmente 53 para DNS o 80 para HTTP).
     * @param timeoutMs Límite de tiempo de espera en milisegundos.
     * @return Latencia RTT medida en milisegundos, o -1f en caso de excepción por timeout o red caída.
     */
    private fun measureTcpLatency(ip: String, port: Int, timeoutMs: Int = 800): Float {
        val socket = java.net.Socket()
        val startTime = System.nanoTime()
        return try {
            val socketAddress = java.net.InetSocketAddress(ip, port)
            // Intenta el establecimiento del canal (TCP Handshake)
            socket.connect(socketAddress, timeoutMs)
            val endTime = System.nanoTime()
            // Convertir nanosegundos de delta a milisegundos flotantes
            (endTime - startTime) / 1_000_000f
        } catch (e: java.net.ConnectException) {
            // Si el host rechaza activamente la conexión (Connection Refused), significa que está vivo
            // y respondió el paquete TCP RST, lo que representa un RTT válido de red.
            val endTime = System.nanoTime()
            (endTime - startTime) / 1_000_000f
        } catch (e: Exception) {
            -1f
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Generador de datos sintéticos de alta fidelidad para simulación del comportamiento de red.
     * Se activa automáticamente como fallback cuando el dispositivo se queda sin red física o WiFi.
     * Emula un comportamiento realista que incluye ruidos de banda base y picos esporádicos de lag.
     *
     * @param isLan True para simular latencia local al gateway (2-8 ms). False para simular latencia WAN (18-32 ms).
     * @return Latencia simulada en milisegundos.
     */
    private fun getSimulatedLatency(isLan: Boolean): Float {
        return if (isLan) {
            val base = 2.0f + random.nextFloat() * 6.0f
            // 2% de probabilidad de tener un pico de retraso provocado por interferencias
            val isSpike = random.nextFloat() < 0.02f
            if (isSpike) base + 35f + random.nextFloat() * 40f else base
        } else {
            val base = 18.0f + random.nextFloat() * 14.0f
            // 3% de probabilidad de tener un pico de retraso WAN provocado por congestión
            val isSpike = random.nextFloat() < 0.03f
            if (isSpike) base + 60f + random.nextFloat() * 70f else base
        }
    }

    /**
     * Expone una copia inmutable del búfer circular de historial para evitar
     * excepciones de modificación concurrente en los hilos de renderizado UI.
     */
    fun getHistoryCopy(): List<LatencyPoint> {
        return synchronized(history) { ArrayList(history) }
    }

    /**
     * Expone una copia inmutable de la bitácora de eventos de roaming para la UI.
     */
    fun getRoamEventsCopy(): List<String> {
        return synchronized(roamEvents) { ArrayList(roamEvents) }
    }

    /**
     * Actualiza el contenido de texto y estado de la notificación activa de este servicio.
     *
     * @param title Nuevo título de la notificación.
     * @param text Nuevo mensaje descriptivo a mostrar.
     */
    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    /**
     * Genera y configura la notificación persistente vinculada al Foreground Service.
     * Incluye botones de acción rápida para detener el monitoreo directamente desde el panel del sistema.
     *
     * @param title Título a desplegar.
     * @param text Texto descriptivo.
     * @return Instancia estructurada de [Notification].
     */
    private fun buildNotification(title: String, text: String): Notification {
        // Redirige al usuario de vuelta a la MainActivity al presionar la notificación
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción de detención directa enviando un comando con la acción ACTION_STOP_SERVICE
        val stopIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Evita que el usuario descarte la notificación deslizando
            .setOnlyAlertOnce(true) // No reproduce sonido en cada actualización de datos
            .setPriority(NotificationCompat.PRIORITY_LOW) // Evita interrumpir visualmente
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .build()
    }

    /**
     * Crea un canal de notificación en el sistema (requerido a partir de Android Oreo - API 26)
     * para clasificar el tipo de notificaciones continuas del monitor de red.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal de Monitoreo de Red",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado activo de monitoreo de red y latencia"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Detiene la ejecución del Foreground Service de manera controlada y limpia.
     * Cancela el bucle periódico y elimina la notificación permanente del panel de tareas.
     */
    private fun stopForegroundService() {
        // Cancelar únicamente el trabajo (job) activo de muestreo periódico.
        // Mantenemos el serviceScope vivo para que la misma instancia del servicio pueda
        // reiniciar el monitoreo en el futuro si se le asocia de nuevo.
        job?.cancel()
        job = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Garantizar la liberación total de recursos y la detención de corrutinas activas
        job?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        /**
         * Identificador único del canal de notificaciones.
         */
        const val CHANNEL_ID = "network_monitor_channel"

        /**
         * Identificador de la notificación persistente del servicio.
         */
        const val NOTIFICATION_ID = 101

        /**
         * Nombre del comando de acción para detener el servicio de manera remota.
         */
        const val ACTION_STOP_SERVICE = "com.abdapps.visorwifi.ACTION_STOP_SERVICE"
    }
}
