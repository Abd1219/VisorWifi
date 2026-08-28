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
*   **Selector de Modos Neo-Brutalista (`ModeSwitcher`)**: Barra superior de alternancia intuitiva y rápida para cambiar entre la vista de **MONITOR** de latencia en vivo y el módulo de localización **AP LOCATOR**.
*   **Módulo AP Locator (Localización Estimada de Access Points)**:
    *   **Funcionamiento Dual e Independiente**: Funciona sincronizado con el servicio en segundo plano o de forma independiente mediante [WifiInfoProvider](app/src/main/java/com/abdapps/visorwifi/provider/WifiInfoProvider.kt) y [WifiSnapshot](app/src/main/java/com/abdapps/visorwifi/model/WifiSnapshot.kt) con polling directo.
    *   **Selección de BSSID**: Muestra el AP actualmente conectado (SSID, BSSID, fabricante OUI, frecuencia, canal y RSSI) y permite seleccionar BSSIDs específicos visibles.
    *   **Calibración y Rotación 360°**: Guía al usuario para realizar un escaneo de orientación mientras mide continuamente el RSSI y calcula la cobertura angular.
    *   **Motor de Estimación de Dirección (RssiDirectionAnalyzer)**: Algoritmo determinista puro que procesa las muestras mediante *Filtro de Mediana*, *Media Móvil*, *Binning Angular de 10°* y *Centroide Ponderado Circular*.
    *   **Brújula Neo-Brutalista Interactiva**: Renderizado en Canvas Compose con doble referencia angular (azimut del teléfono vs flecha compensada en tiempo real hacia la dirección estimada del AP).
    *   **Calculadora de Confianza (0–100%)**: Evalúa 5 factores clave (cantidad de muestras, rango RSSI, varianza de zona de pico, cobertura angular y consistencia) para informar la fiabilidad de la medición.
    *   **Historial de Puntos de Medición**: Registra las estimaciones por posición física durante la sesión para comparación en campo.
*   **Motor de Diagnóstico Híbrido con Inteligencia Artificial**:
    *   **Fase 1 (Heurísticas)**: Reglas físicas instantáneas basadas en límites y umbrales rígidos para detectar anomalías inmediatas.
    *   **Fase 2 (IA Local y Determinista)**: Clasificador ligero de IA ([LightWeightClassifier](app/src/main/java/com/abdapps/visorwifi/engine/ia/LightWeightClassifier.kt)) que procesa ventanas estadísticas de telemetría de hasta 300 segundos.
    *   **Fusión e Inferencia Híbrida**: Un orquestador inteligente ([HybridDiagnosisEngine](app/src/main/java/com/abdapps/visorwifi/engine/ia/HybridDiagnosisEngine.kt)) que combina ambas fases, resuelve discrepancias de diagnóstico y calcula la confianza de la clasificación para identificar causas complejas como *interferencia de canal Wi-Fi*, *señal deficiente* o *problemas del proveedor (ISP)*, ofreciendo recomendaciones dinámicas.
*   **Generador Profesional de Reportes PDF**:
    *   Genera reportes periciales completos de auditoría con estadísticas por periodos, gráficas incrustadas de latencia/RSSI y diagnóstico técnico.
    *   **Sanitización de Glifos y Compatibilidad Tipográfica**: Normaliza ligaduras latinas (`fi`, `fl`, `ff`), en-dashes (`–`), caracteres matemáticos y emojis para evitar fallos de renderizado en el motor PDF Skia de Android.
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
*   **Sensores & Orientación**: `SensorManager` (`TYPE_ACCELEROMETER`, `TYPE_MAGNETIC_FIELD`, `TYPE_ROTATION_VECTOR`)
*   **Generación de Documentos**: `android.graphics.pdf.PdfDocument` con renderizado vectorial nativo
*   **Componentes de Android**:
    *   `Service` (Foreground Service persistente)
    *   `NotificationManager` & `NotificationChannel` (API 26+)
    *   `WifiManager` & `WifiInfo` (Telemetría de enlace)
    *   `ServiceConnection` (Binding y sincronización con estados Compose)
*   **Visualización**: [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) y Canvas nativo de Compose para la brújula interactiva

---

## 📂 Estructura del Código Fuente

El código fuente está completamente documentado y comentado en español bajo el estándar de **KDoc** para facilitar su comprensión:

*   **Modelo de Datos**:
    *   [LatencyPoint.kt](app/src/main/java/com/abdapps/visorwifi/model/LatencyPoint.kt): Almacena la telemetría recolectada por segundo (LAN, WAN, RSSI, BSSID, SSID, PHY Speeds).
    *   [WifiSnapshot.kt](app/src/main/java/com/abdapps/visorwifi/model/WifiSnapshot.kt): Snapshot desacoplado del adaptador Wi-Fi.
    *   [PdfGenerator.kt](app/src/main/java/com/abdapps/visorwifi/model/PdfGenerator.kt): Motor de renderizado y sanitización de reportes PDF.
    *   [Modelos AP Locator](app/src/main/java/com/abdapps/visorwifi/model/aplocator/): Modelos inmutables (`RssiOrientationSample`, `DirectionEstimate`, `MeasurementPoint`, `ApInfo`, `ApLocatorSession`).
*   **Servicio & Proveedores de Datos**:
    *   [NetworkMonitorService.kt](app/src/main/java/com/abdapps/visorwifi/NetworkMonitorService.kt): Monitoreo a 1Hz, fallbacks TCP, búfer circular e historial de roaming.
    *   [WifiInfoProvider.kt](app/src/main/java/com/abdapps/visorwifi/provider/WifiInfoProvider.kt): Consultas instantáneas de red desacopladas del servicio.
    *   [SensorOrientationManager.kt](app/src/main/java/com/abdapps/visorwifi/aplocator/SensorOrientationManager.kt): Gestor de sensores de acelerómetro y magnetómetro con suavizado de paso bajo.
*   **Motor AP Locator & Diagnóstico**:
    *   [RssiDirectionAnalyzer.kt](app/src/main/java/com/abdapps/visorwifi/engine/aplocator/RssiDirectionAnalyzer.kt): Algoritmo determinista puro de filtrado y estimación angular.
    *   [ConfidenceCalculator.kt](app/src/main/java/com/abdapps/visorwifi/engine/aplocator/ConfidenceCalculator.kt): Ponderador de 5 factores de confianza de estimación.
    *   [NetworkDiagnosisEngine.kt](app/src/main/java/com/abdapps/visorwifi/engine/NetworkDiagnosisEngine.kt): Motor de Fase 1 para diagnósticos heurísticos inmediatos.
    *   [HybridDiagnosisEngine.kt](app/src/main/java/com/abdapps/visorwifi/engine/ia/HybridDiagnosisEngine.kt): Orquestador híbrido heurística + IA.
*   **Interfaz de Usuario (UI)**:
    *   [MainActivity.kt](app/src/main/java/com/abdapps/visorwifi/MainActivity.kt): Actividad principal con navegación Compose.
    *   [ModeSwitcher.kt](app/src/main/java/com/abdapps/visorwifi/ui/components/ModeSwitcher.kt): Selector visual superior entre Monitor y AP Locator.
    *   [LatencyMonitorScreen.kt](app/src/main/java/com/abdapps/visorwifi/ui/screens/LatencyMonitorScreen.kt): Pantalla principal de monitoreo en tiempo real.
    *   [ApLocatorScreen.kt](app/src/main/java/com/abdapps/visorwifi/ui/screens/ApLocatorScreen.kt): Pantalla declarativa completa del módulo AP Locator (3 estados).
    *   [CompassView.kt](app/src/main/java/com/abdapps/visorwifi/ui/components/aplocator/CompassView.kt): Brújula nativa interactiva en Canvas Compose.

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
