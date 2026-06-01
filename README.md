# VisorWifi 📡📶

**VisorWifi** es una aplicación móvil moderna para Android diseñada para monitorear y analizar el rendimiento, la latencia, el jitter y la itinerancia (roaming) de redes inalámbricas en tiempo real. 

Construida bajo los estándares modernos de desarrollo de Android con **Jetpack Compose** para una interfaz neo-brutalista premium y **Corrutinas de Kotlin** para el procesamiento concurrente eficiente en segundo plano.

---

## 🚀 Características Principales

*   **Monitoreo en Segundo Plano Continuo**: Funciona mediante un servicio en primer plano (**Foreground Service**) con una notificación persistente e interactiva. Los datos se siguen registrando de forma ininterrumpida incluso si sales de la aplicación o apagas la pantalla.
*   **Medición Concurrente de Latencia (LAN & WAN)**:
    *   **LAN (Acceso Local)**: Mide el tiempo de respuesta (RTT) hacia la puerta de enlace (Gateway) del router de forma periódica cada segundo.
    *   **WAN (Internet)**: Mide el tiempo de respuesta hacia los servidores DNS públicos primarios (DNS de Google `8.8.8.8` y Cloudflare `1.1.1.1`).
*   **Cálculo y Visualización de Jitter de Red**: Incorpora un overlay visual sumamente tenue en la gráfica de latencia que muestra el Jitter (variabilidad de la latencia) en tiempo real para un análisis profundo de la inestabilidad de la transmisión sin generar ruido visual en la UI.
*   **Métricas de Pérdida de Paquetes**: Calcula dinámicamente el porcentaje de pérdida de paquetes sobre una ventana móvil de los últimos 100 puntos y despliega badges interactivos de alerta (naranja/rojo) en caso de detectar fallas o timeouts.
*   **Motor de Diagnóstico Híbrido con Inteligencia Artificial**:
    *   **Fase 1 (Heurísticas)**: Reglas físicas instantáneas basadas en límites y umbrales rígidos para detectar anomalías inmediatas.
    *   **Fase 2 (IA Local y Determinista)**: Clasificador ligero de IA ([LightWeightClassifier](app/src/main/java/com/abdapps/visorwifi/engine/ia/LightWeightClassifier.kt)) que procesa ventanas estadísticas de telemetría de hasta 300 segundos.
    *   **Fusión e Inferencia Híbrida**: Un orquestador inteligente ([HybridDiagnosisEngine](app/src/main/java/com/abdapps/visorwifi/engine/ia/HybridDiagnosisEngine.kt)) que combina ambas fases, resuelve discrepancias de diagnóstico y calcula la confianza de la clasificación para identificar causas complejas como *interferencia de canal Wi-Fi*, *señal deficiente* o *problemas del proveedor (ISP)*, ofreciendo recomendaciones dinámicas.
*   **Mecanismos de Resiliencia (Fallback)**:
    *   **Ping ICMP nativo**: Ejecuta comandos de consola del sistema (`/system/bin/ping`) para obtener RTTs exactos.
    *   **Sockets TCP**: Si el tráfico ICMP/ping está bloqueado por el firewall de la red local, la app realiza un fallback automático iniciando conexiones TCP rápidas en puertos comunes (DNS `53` y HTTP `80`).
    *   **Simulación Telemétrica**: Si el adaptador de red está apagado o no hay WiFi disponible, se activa un generador de ruido blanco de alta fidelidad que simula latencias con fluctuaciones y picos reales para pruebas de desarrollo.
*   **Historial de Intensidad de Señal RSSI**: Incorpora un gráfico dedicado ([RssiChart](app/src/main/java/com/abdapps/visorwifi/ui/components/RssiChart.kt)) que mapea en tiempo real la señal en dBm sobre líneas de umbrales técnicos preestablecidos (Bueno: -60 dBm, Regular: -70 dBm, Malo: -80 dBm) codificados por colores.
*   **Detección de Roaming y Transición de Red**: 
    *   Registra en tiempo real cuando el dispositivo realiza itinerancia (**Roaming** inalámbrico al cambiar de antena/BSSID dentro del mismo SSID).
    *   Registra cambios de red completos (**SSID**) detallando la fuerza de la señal al momento de la transición.
*   **Telemetría WiFi Avanzada**: 
    *   **Intensidad de Señal (RSSI)** cualitativa (Excelente, Muy Buena, Buena, Regular, Débil) y cuantitativa en dBm.
    *   **Banda de Frecuencia**: Clasificación automática del canal (2.4 GHz, 5 GHz y 6 GHz).
    *   **Velocidades Físicas (PHY Link Speeds)**: Tasas teóricas de transmisión (TX) y recepción (RX) en Mbps actualizadas en tiempo real (disponibles en API 29+).

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
*   **Visualización**: [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) (Gráficas de alto rendimiento integradas en Compose via `AndroidView`)

---

## 📂 Estructura del Código Fuente

El código fuente está completamente documentado y comentado en español bajo el estándar de **KDoc** para facilitar su comprensión:

*   **Modelo de Datos**:
    *   [LatencyPoint.kt](app/src/main/java/com/abdapps/visorwifi/model/LatencyPoint.kt): Almacena la telemetría recolectada por segundo (LAN, WAN, RSSI, BSSID, SSID, PHY Speeds).
*   **Servicio & Lógica**:
    *   [NetworkMonitorService.kt](app/src/main/java/com/abdapps/visorwifi/NetworkMonitorService.kt): Gestiona el ciclo periódico de monitoreo a 1Hz, ejecuta pings/fallbacks de sockets, almacena el buffer histórico de sesión y expone los flujos reactivos.
*   **Motor de Inteligencia Artificial (IA)**:
    *   [NetworkDiagnosisEngine.kt](app/src/main/java/com/abdapps/visorwifi/engine/NetworkDiagnosisEngine.kt): Motor de Fase 1 para diagnósticos inmediatos basados en límites heurísticos rígidos.
    *   [HybridDiagnosisEngine.kt](app/src/main/java/com/abdapps/visorwifi/engine/ia/HybridDiagnosisEngine.kt): Orquestador híbrido que fusiona los estados heurísticos (Fase 1) y los de IA (Fase 2).
    *   [LightWeightClassifier.kt](app/src/main/java/com/abdapps/visorwifi/engine/ia/LightWeightClassifier.kt): Clasificador local por puntuación estadística ponderada para determinar la salud y atribución de problemas de red.
*   **Interfaz de Usuario (UI)**:
    *   [MainActivity.kt](app/src/main/java/com/abdapps/visorwifi/MainActivity.kt): Actividad principal, gestiona permisos dinámicos de ubicación y notificaciones, y controla el enlace bidireccional seguro del servicio.
    *   [LatencyMonitorScreen.kt](app/src/main/java/com/abdapps/visorwifi/ui/screens/LatencyMonitorScreen.kt): Pantalla principal Neo-Brutalista que unifica todas las tarjetas, flujos reactivos de Compose y paneles.
    *   [LatencyChart.kt](app/src/main/java/com/abdapps/visorwifi/ui/components/LatencyChart.kt): Renderizador nativo interoperable de latencias (LAN/WAN) con soporte dinámico para overlays de Jitter.
    *   [RssiChart.kt](app/src/main/java/com/abdapps/visorwifi/ui/components/RssiChart.kt): Gráfica nativa en tiempo real dedicada a mapear el comportamiento del RSSI.

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
