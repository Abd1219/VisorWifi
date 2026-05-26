package com.abdapps.visorwifi

import com.abdapps.visorwifi.ui.screens.LatencyMonitorScreen
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.abdapps.visorwifi.ui.theme.VisorWifiTheme

/**
 * Actividad Principal de la aplicación VisorWifi.
 *
 * Actúa como punto de entrada de la interfaz de usuario. Administra:
 * 1. El ciclo de vida de conexión con el servicio en segundo plano ([NetworkMonitorService]).
 * 2. La inicialización y vinculación del servicio persistente en primer plano.
 * 3. La renderización de la pantalla principal pasándole las dependencias y estados limpios.
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

        // Al crear la actividad, intenta enlazar al servicio si aún no está enlazado.
        // Eliminamos la verificación de actividad que puede ser inexacta.
        if (!isServiceBound.value) {
            bindToService()
        }

        setContent {
            VisorWifiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0B0E14)
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
        // Al volver a primer plano, enlazamos al servicio si no está ya enlazado.
        if (!isServiceBound.value) {
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
        // Limpiar explícitamente la referencia del servicio solo cuando el usuario lo detiene
        serviceInstance.value = null
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
     * CORRECCIÓN: Preservamos el estado del servicio en memoria (serviceInstance) para
     * que la gráfica no pierda datos al pasar por el ciclo onStop/onStart.
     * Solo limpiamos serviceInstance cuando el usuario detiene explícitamente el servicio.
     */
    private fun unbindFromService() {
        if (isServiceBound.value) {
            unbindService(connection)
            isServiceBound.value = false
            // NO ponemos serviceInstance.value = null aquí para no borrar la gráfica
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