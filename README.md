# VisorWifi 📡📶

**VisorWifi** es una aplicación móvil moderna para Android diseñada para monitorear y analizar el rendimiento, la latencia y la itinerancia (roaming) de redes inalámbricas en tiempo real. 

Construida bajo los estándares modernos de desarrollo de Android con **Jetpack Compose** para una interfaz neo-brutalista premium y **Corrutinas de Kotlin** para el procesamiento concurrente eficiente en segundo plano.

---

## 🚀 Características Principales

*   **Monitoreo en Segundo Plano Continuo**: Funciona mediante un servicio en primer plano (**Foreground Service**) con una notificación persistente e interactiva. Los datos se siguen registrando incluso si sales de la aplicación o apagas la pantalla.
*   **Medición Concurrente de Latencia ( LAN & WAN )**:
    *   **LAN (Acceso Local)**: Mide el tiempo de respuesta (RTT) hacia la puerta de enlace (Gateway) del router de forma periódica cada segundo.
    *   **WAN (Internet)**: Mide el tiempo de respuesta hacia los servidores DNS públicos primarios (DNS de Google `8.8.8.8` y Cloudflare `1.1.1.1`).
*   **Mecanismos de Resiliencia (Fallback)**:
    *   **Ping ICMP nativo**: Ejecuta comandos de consola del sistema (`/system/bin/ping`) para obtener RTTs exactos.
    *   **Sockets TCP**: Si el tráfico ICMP/ping está bloqueado por el firewall de la red local, la app realiza un fallback automático iniciando conexiones TCP rápidas en puertos comunes (DNS `53` y HTTP `80`).
    *   **Simulación Telemétrica**: Si el adaptador de red está apagado o no hay WiFi disponible, se activa un generador de ruido blanco de alta fidelidad que simula latencias con fluctuaciones y picos reales para pruebas de desarrollo.
*   **Detección de Roaming y Transición de Red**: 
    *   Registra en tiempo real cuando el dispositivo realiza itinerancia (**Roaming** inalámbrico al cambiar de antena/BSSID dentro del mismo SSID).
    *   Registra cambios de red completos (**SSID**) detallando la fuerza de la señal al momento de la transición.
*   **Telemetría WiFi Avanzada**: 
    *   **Intensidad de Señal (RSSI)** cualitativa (Excelente, Muy Buena, Buena, Regular, Débil) y cuantitativa en dBm.
    *   **Banda de Frecuencia**: Clasificación automática del canal (2.4 GHz, 5 GHz y 6 GHz).
    *   **Velocidades Físicas (PHY Link Speeds)**: Tasas teóricas de transmisión (TX) y recepción (RX) en Mbps actualizadas en tiempo real (disponibles en API 29+).
*   **Gráfica Neo-Brutalista en Tiempo Real**: Incorpora **MPAndroidChart** integrado en Compose mediante un `AndroidView`. Muestra curvas suaves de Bézier cúbicas con sombreado de área degradado para las latencias LAN y WAN, y scroll automático acotado a los últimos 60 segundos.

---

## 🛠️ Stack Tecnológico

*   **Lenguaje**: [Kotlin](https://kotlinlang.org/)
*   **Interfaz de Usuario**: [Jetpack Compose](https://developer.android.com/compose) (Material 3)
*   **Concurrencia**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) (`async`/`await`, `SupervisorJob`, `Dispatchers.IO`)
*   **Reactividad**: [Kotlin Flows](https://kotlinlang.org/docs/flow.html) (`MutableSharedFlow`, reactividad sin hilos de UI bloqueados)
*   **Componentes de Android**:
    *   `Service` (Foreground Service persistente)
    *   `NotificationManager` & `NotificationChannel` (API 26+)
    *   `WifiManager` & `WifiInfo` (Telemetría de enlace)
    *   `ServiceConnection` (Binding y sincronización con estados Compose)
*   **Visualización**: [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) (Gráficas de alto rendimiento)

---

## 📂 Estructura del Código Fuente

El código fuente está completamente documentado y comentado en español bajo el estándar de **KDoc** para facilitar su comprensión:

*   [LatencyPoint.kt](app/src/main/main/java/com/abdapps/visorwifi/LatencyPoint.kt): Modelo de datos inmutable que almacena la marca de tiempo, latencias locales/externas y telemetría de red.
*   [NetworkMonitorService.kt](app/src/main/main/java/com/abdapps/visorwifi/NetworkMonitorService.kt): Servicio en segundo plano que ejecuta el bucle de monitoreo de 1 segundo, gestiona pings/TCP fallbacks, almacena el buffer circular histórico y expone flujos reactivos.
*   [MainActivity.kt](app/src/main/main/java/com/abdapps/visorwifi/MainActivity.kt): Actividad que implementa la UI neo-brutalista, gestiona la petición de permisos dinámicos (Ubicación y Notificaciones) e integra la gráfica en tiempo real interactiva.

---

## 🔨 Compilación y Ejecución

El proyecto incluye un script de compilación rápido (`compile.bat`) para compilar sin necesidad de abrir Android Studio completo.

### Requisitos
*   Dispositivo físico Android o Emulador (se recomienda dispositivo físico para obtener lecturas reales de RSSI, SSID y BSSID).
*   Permisos necesarios en el dispositivo:
    *   **Ubicación Precisa (`ACCESS_FINE_LOCATION`)**: Requerido por Android para poder leer el SSID y la dirección MAC (BSSID) del punto de acceso.
    *   **Notificaciones (`POST_NOTIFICATIONS`)**: Requerido en Android 13+ para mostrar la notificación del Foreground Service.

### Compilar desde consola (Windows)
1.  Asegúrate de que la ruta del JDK embebido de Android Studio sea la correcta en tu sistema.
2.  Ejecuta el siguiente comando en la terminal para compilar el código Kotlin:
    ```powershell
    .\compile.bat compileDebugKotlin
    ```
3.  Para compilar el APK de depuración completo:
    ```powershell
    .\compile.bat assembleDebug
    ```

---

## 📜 Licencia

Desarrollado de manera abierta por **AbdApps** para fines educativos y auditorías de conectividad de red.
