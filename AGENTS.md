# VisorWifi — AI Agent Guidelines & Project Context (`AGENTS.md`)

Este archivo sirve como referencia principal para asistentes de IA (Antigravity, Gemini, Copilot, etc.) que trabajen en la base de código de **VisorWifi**.

---

## 📌 Contexto General del Proyecto

- **Nombre**: VisorWifi
- **Tipo de Aplicación**: Diagnóstico, monitoreo y análisis de rendimiento de redes Wi-Fi en tiempo real para técnicos e ingenieros de red.
- **Lenguaje**: Kotlin (Kotlin DSL para Gradle)
- **UI Framework**: Jetpack Compose con Material 3 (Estilo **Neo-Brutalista** en Dark Mode: fondo `#0B0E14`, color primario `#00F0FF`, acento `#00FF66`).
- **SDK**: `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`.
- **Concurrencia**: Kotlin Coroutines (`Dispatchers.IO`, `SupervisorJob`) + `SharedFlow` / `StateFlow`.
- **Gráficas**: MPAndroidChart (vía `AndroidView` en Compose) y Canvas nativo de Compose para componentes interactivos como `CompassView` y `RssiChart`.

---

## 🏗️ Estructura de Paquetes (`com.abdapps.visorwifi`)

```text
com.abdapps.visorwifi
├── MainActivity.kt                # Punto de entrada, gestión de permisos y navegación Compose simple
├── NetworkMonitorService.kt       # Foreground Service de monitoreo a 1Hz (latencias LAN/WAN, RSSI, roaming)
│
├── aplocator/                     # Módulo AP Locator — Capa de sensores
│   └── SensorOrientationManager.kt  # Adquisición de acelerómetro + magnetómetro con filtro de paso bajo
│
├── analysis/                      # Analizador de periodos para reportes
│   ├── PeriodAnalyzer.kt          # Estadísticas agregadas de sesión (función pura)
│   └── PeriodStatusEvaluator.kt   # Evaluación del estado del periodo para PDF
│
├── engine/                        # Motores de análisis y diagnóstico
│   ├── NetworkDiagnosisEngine.kt  # Diagnóstico heurístico Fase 1 (reglas rígidas)
│   ├── aplocator/
│   │   ├── ConfidenceCalculator.kt# Ponderador de 5 factores de confianza (0-100%)
│   │   └── RssiDirectionAnalyzer.kt # Algoritmo determinista RSSI + orientación (función pura)
│   └── ia/                        # Prototipo heurístico inactivo; no es IA real
│       ├── NetworkFeatures.kt     # Extracción de características telemétricas
│       ├── LightWeightClassifier.kt# Clasificador local determinista por puntuación
│       ├── IaDiagnosisResult.kt   # Resultado del clasificador heurístico experimental
│       └── HybridDiagnosisEngine.kt# Prototipo de combinación no conectado al monitor
│
├── model/                         # Modelos de datos inmutables
│   ├── LatencyPoint.kt            # Telemetría instantánea por segundo
│   ├── NetworkMetrics.kt          # Métricas consolidadas para diagnóstico
│   ├── DiagnosisState.kt          # Estado de diagnóstico para UI
│   ├── ReportData.kt              # Datos estructurados para generación de PDF
│   ├── PdfGenerator.kt            # Reporte PDF v2: 3 páginas, gráficas escaladas proporcionalmente y sanitización
│   ├── WifiSnapshot.kt            # Captura desacoplada e instantánea del enlace Wi-Fi
│   └── aplocator/                 # Modelos del módulo AP Locator
│       ├── RssiOrientationSample.kt# Muestra atómica (azimut, RSSI, timestamp, BSSID, freq)
│       ├── DirectionEstimate.kt   # Estimación de dirección (azimut, confianza, warnings)
│       ├── MeasurementPoint.kt    # Punto posicional de medición
│       ├── ApLocatorSession.kt    # Sesión en memoria con promedio circular
│       └── ApInfo.kt              # Información técnica del AP y lookup OUI de fabricantes
│
├── provider/                      # Proveedores desacoplados de información de sistema
│   └── WifiInfoProvider.kt        # Consulta directa de WifiInfo y escaneo sin Foreground Service
│
├── ui/                            # Capa de presentación (Jetpack Compose)
│   ├── screens/
│   │   ├── LatencyMonitorScreen.kt# Pantalla principal de monitoreo en tiempo real
│   │   └── ApLocatorScreen.kt     # Pantalla declarativa de AP Locator (SELECT_AP, SCANNING, RESULT)
│   ├── components/
│   │   ├── ModeSwitcher.kt        # Selector superior Neo-Brutalista de pantallas (Monitor / AP Locator)
│   │   ├── WifiInfoCard.kt        # Tarjeta de detalles del enlace Wi-Fi
│   │   ├── DiagnosisCard.kt       # Tarjeta de diagnóstico de red
│   │   ├── LatencyCard.kt         # Tarjeta de métrica instantánea LAN/WAN
│   │   ├── LatencyChart.kt        # Gráfica MPAndroidChart de latencia y jitter
│   │   ├── RssiChart.kt           # Gráfica MPAndroidChart de RSSI histórico
│   │   ├── RoamingEventLog.kt     # Log visual de eventos de roaming/cambio de red
│   │   ├── ReportGenerationFlow.kt# Diálogos de entrada y vista previa de PDF
│   │   └── aplocator/             # Componentes UI de AP Locator
│   │       ├── CompassView.kt     # Brújula en Canvas Compose con compensación de azimut
│   │       ├── RssiSignalIndicator.kt # Indicador de señal con 10 segmentos
│   │       ├── ConfidenceBar.kt   # Barra de confianza animada
│   │       ├── ApSelectionCard.kt # Selector e info técnica de BSSIDs
│   │       ├── ScanProgressView.kt# Vista de progreso durante calibración 360°
│   │       └── MeasurementHistoryList.kt # Lista de puntos medidos en la sesión
│   └── theme/
│       ├── Color.kt, Theme.kt, Type.kt # Sistema de diseño visual
│
└── viewmodel/
    ├── NetworkDiagnosisViewModel.kt# ViewModel del diagnóstico de red
    └── ApLocatorViewModel.kt       # ViewModel del módulo AP Locator (con polling independiente)
```

---

## ⚙️ Reglas Arquitectónicas y Convenciones de Desarrollo

### 1. Inmutabilidad y Funciones Puras en Motores
- Los motores de análisis deben evitar dependencias del Android SDK (`Context`, `View`, `SensorManager`, `Log`) para ser testeables como lógica JVM.
- El clasificador experimental y el combinador no deben añadir dependencias del Android SDK; actualmente no usan `android.util.Log`.

### 2. Estado del diagnóstico e IA
- El diagnóstico actual es heurístico y determinista. `LightWeightClassifier` aplica reglas de puntuación; pese al nombre del paquete `engine/ia`, no es un modelo de IA ni aprendizaje automático.
- El monitor y los reportes usan `NetworkDiagnosisEngine`. El código bajo `engine/ia` contiene un prototipo heurístico, pero no está conectado al flujo actual.
- La implementación de IA real es trabajo futuro. Actualizar la documentación si cambia este estado.
- `LatencyPoint.lanLatency` y `wanLatency` representan ICMP. Los tiempos y destinos TCP complementarios van en campos separados y no se deben mezclar con promedios, jitter o pérdida ICMP.

### 2.1 Reporte PDF v2
- `PdfGenerator` es únicamente una capa de presentación; recibe `ReportData` consolidado y no debe recalcular métricas ni alterar diagnóstico.
- El reporte tiene tres páginas: resumen/diagnóstico, métricas agrupadas y evidencia/cierre.
- Las gráficas se crean como bitmaps off-screen de alta resolución y se insertan preservando su relación de aspecto dentro del área segura A4. Nunca usar dimensiones de píxel como puntos PDF ni recortar ejes o leyendas.

### 3. No Romper APIs Deprecadas de Compatibilidad
- `WifiManager.connectionInfo` y `ScanResult.SSID` están anotados con `@Suppress("DEPRECATION")` intencionalmente para mantener compatibilidad desde `minSdk 26`. NO refactorizar ni eliminar estas llamadas a menos que sea explícitamente solicitado.

### 4. Concurrencia y Reactividad
- Usar `SharedFlow` / `StateFlow` para emitir datos telemétricos desde `NetworkMonitorService`.
- Evitar operaciones de E/S o pings en el hilo principal (`Dispatchers.IO`).
- Usar `SupervisorJob` en corrutinas de servicio para resiliencia ante fallas puntuales de red.

### 5. Honestidad Técnica en AP Locator
- **NUNCA** afirmar que la funcionalidad es un sistema de *Direction of Arrival (DoA)* ni que conoce el *ángulo físico real*.
- Usar siempre términos: *Dirección estimada*, *Zona probable*, *Confianza de estimación*.
- La implementación actual detiene la captura cuando el BSSID seleccionado difiere del BSSID conectado; no afirmar que estima APs cercanos no conectados.

### 6. Estilo Visual y Tema
- Preservar el tema **Dark Mode Neo-Brutalista**:
  - Fondo de pantalla: `Color(0xFF0B0E14)`
  - Contenedores / Tarjetas: `Color(0xFF121620)` / `Color(0xFF161C26)`
  - Color primario (Cian vibrante): `Color(0xFF00F0FF)`
  - Verde de estado saludable / excelente: `Color(0xFF00FF66)`
  - Bordes finos: `1.dp` con `Color(0xFF1E293B)` o `Color(0xFF263238)`

---

## 🧪 Ejecución de Pruebas Unitarias y Compilación

Para ejecutar pruebas unitarias desde consola en Windows utilizando la JDK de Android Studio:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew :app:testDebugUnitTest --tests "com.abdapps.visorwifi.aplocator.*"
```

Para realizar la compilación completa de depuración:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```
