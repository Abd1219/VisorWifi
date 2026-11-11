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
*   **Pérdida de respuestas ICMP**: Calcula el porcentaje de respuestas ICMP perdidas sobre una ventana móvil de los últimos 100 puntos. Las comprobaciones TCP se presentan aparte y no cuentan como respuestas ICMP.
*   **Selector de Modos Neo-Brutalista (`ModeSwitcher`)**: Barra superior de alternancia intuitiva y rápida para cambiar entre la vista de **MONITOR** de latencia en vivo y el módulo de localización **AP LOCATOR**.
*   **Módulo AP Locator (Localización Estimada de Access Points)**:
    *   **Funcionamiento Dual e Independiente**: Funciona sincronizado con el servicio en segundo plano o de forma independiente mediante [WifiInfoProvider](app/src/main/java/com/abdapps/visorwifi/provider/WifiInfoProvider.kt) y [WifiSnapshot](app/src/main/java/com/abdapps/visorwifi/model/WifiSnapshot.kt) con polling directo.
    *   **Selección de BSSID**: Muestra el AP conectado y resultados de escaneos Wi-Fi almacenados por Android. La medición de dirección solo funciona para el AP al que el teléfono está conectado; seleccionar otro BSSID hace que el escaneo se detenga al detectar que no coincide.
    *   **Calibración y Rotación 360°**: Guía al usuario para acumular muestras de RSSI y orientación durante el giro. La estimación depende de las lecturas del dispositivo y no determina el ángulo físico real del AP.
    *   **Motor de Estimación de Dirección (RssiDirectionAnalyzer)**: Algoritmo determinista puro que procesa las muestras mediante *Filtro de Mediana*, *Media Móvil*, *Binning Angular de 10°* y *Centroide Ponderado Circular*.
    *   **Brújula Neo-Brutalista Interactiva**: Renderizado en Canvas Compose con doble referencia angular (azimut del teléfono vs flecha compensada en tiempo real hacia la dirección estimada del AP).
    *   **Calculadora de Confianza (0–100%)**: Evalúa 5 factores clave (cantidad de muestras, rango RSSI, varianza de zona de pico, cobertura angular y consistencia) para informar la fiabilidad de la medición.
    *   **Historial de Puntos de Medición**: Registra las estimaciones por posición física durante la sesión para comparación en campo.
*   **Diagnóstico heurístico de red**: El monitor y los reportes usan reglas deterministas basadas en latencia ICMP, RSSI, jitter y pérdida. No hay IA implementada ni conectada al flujo actual. Bajo `engine/ia` se conserva un prototipo de puntuación heurística no utilizado; una IA real queda como trabajo futuro.
*   **Generador Profesional de Reportes PDF**:
    *   Genera un reporte técnico de tres páginas: resumen ejecutivo y diagnóstico, métricas detalladas, y evidencia gráfica con eventos, observaciones y firmas.
    *   Conserva las métricas del periodo (LAN, WAN, pérdida ICMP, jitter, RSSI, PHY, banda, roaming y TCP complementario) sin modificar los cálculos de monitoreo ni el diagnóstico.
    *   Las gráficas de latencia y RSSI se renderizan fuera de pantalla a alta resolución y se insertan proporcionalmente dentro del área útil A4. Usan etiquetas, marcas y leyendas compactas para priorizar los datos sin perder las unidades `ms` y `dBm`.
    *   **Sanitización de Glifos y Compatibilidad Tipográfica**: Normaliza ligaduras latinas (`fi`, `fl`, `ff`), en-dashes (`–`), caracteres matemáticos y emojis para evitar fallos de renderizado en el motor PDF Skia de Android.
    *   **Interpretación para cliente**: Distingue el estado global del periodo del comportamiento predominante; contextualiza picos aislados y crea una conclusión clara sin alterar métricas ni diagnóstico.
*   **Mecanismos de Resiliencia (Fallback)**:
    *   **Ping ICMP**: Intenta medir RTT con `/system/bin/ping`.
    *   **Sonda TCP complementaria**: Si falla ICMP, intenta establecer conexiones TCP en puertos seleccionados. La tarjeta y el PDF muestran ese tiempo por separado; no se mezcla con latencia, jitter ni pérdida ICMP. Un rechazo de conexión cuenta como respuesta del host y su tiempo se registra como tiempo de conexión TCP.
    *   **Sin simulación telemétrica**: Si fallan ICMP y las sondas TCP, se conserva el fallo; la app no inventa valores de latencia.
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
    *   [PdfGenerator.kt](app/src/main/java/com/abdapps/visorwifi/model/PdfGenerator.kt): Motor de renderizado, escalado proporcional de gráficas y sanitización del reporte PDF v2.
    *   [Modelos AP Locator](app/src/main/java/com/abdapps/visorwifi/model/aplocator/): Modelos inmutables (`RssiOrientationSample`, `DirectionEstimate`, `MeasurementPoint`, `ApInfo`, `ApLocatorSession`).
*   **Servicio & Proveedores de Datos**:
    *   [NetworkMonitorService.kt](app/src/main/java/com/abdapps/visorwifi/NetworkMonitorService.kt): Monitoreo a 1Hz, fallbacks TCP, búfer circular e historial de roaming.
    *   [WifiInfoProvider.kt](app/src/main/java/com/abdapps/visorwifi/provider/WifiInfoProvider.kt): Consultas instantáneas de red desacopladas del servicio.
    *   [SensorOrientationManager.kt](app/src/main/java/com/abdapps/visorwifi/aplocator/SensorOrientationManager.kt): Gestor de sensores de acelerómetro y magnetómetro con suavizado de paso bajo.
*   **Motor AP Locator & Diagnóstico**:
    *   [RssiDirectionAnalyzer.kt](app/src/main/java/com/abdapps/visorwifi/engine/aplocator/RssiDirectionAnalyzer.kt): Algoritmo determinista puro de filtrado y estimación angular.
    *   [ConfidenceCalculator.kt](app/src/main/java/com/abdapps/visorwifi/engine/aplocator/ConfidenceCalculator.kt): Ponderador de 5 factores de confianza de estimación.
    *   [NetworkDiagnosisEngine.kt](app/src/main/java/com/abdapps/visorwifi/engine/NetworkDiagnosisEngine.kt): Motor de Fase 1 para diagnósticos heurísticos inmediatos.
    *   [HybridDiagnosisEngine.kt](app/src/main/java/com/abdapps/visorwifi/engine/ia/HybridDiagnosisEngine.kt): Prototipo heurístico no conectado al monitor; no implementa IA real.
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
    *   **Ubicación (`ACCESS_FINE_LOCATION`)**: Android la requiere para ciertas lecturas Wi-Fi, incluido el acceso a SSID/BSSID y resultados de escaneo en las versiones aplicables. La lista de resultados proviene del último escaneo disponible del sistema; la app no fuerza uno nuevo en cada consulta.
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
