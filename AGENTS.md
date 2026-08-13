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
│   └── ia/                        # Motor de diagnóstico por IA local
│       ├── NetworkFeatures.kt     # Extracción de características telemétricas
│       ├── LightWeightClassifier.kt# Clasificador local determinista por puntuación
│       ├── IaDiagnosisResult.kt   # Resultado inferido por IA
│       └── HybridDiagnosisEngine.kt# Fusión híbrida de heurística + IA
│
├── model/                         # Modelos de datos inmutables
│   ├── LatencyPoint.kt            # Telemetría instantánea por segundo
│   ├── NetworkMetrics.kt          # Métricas consolidadas para diagnóstico
│   ├── DiagnosisState.kt          # Estado de diagnóstico para UI
│   ├── ReportData.kt              # Datos estructurados para generación de PDF
│   ├── PdfGenerator.kt            # Generador nativo de archivos PDF
│   └── aplocator/                 # Modelos del módulo AP Locator
│       ├── RssiOrientationSample.kt# Muestra atómica (azimut, RSSI, timestamp, BSSID, freq)
│       ├── DirectionEstimate.kt   # Estimación de dirección (azimut, confianza, warnings)
│       ├── MeasurementPoint.kt    # Punto posicional de medición
│       ├── ApLocatorSession.kt    # Sesión en memoria con promedio circular
│       └── ApInfo.kt              # Información técnica del AP y lookup OUI de fabricantes
│
├── ui/                            # Capa de presentación (Jetpack Compose)
│   ├── screens/
│   │   ├── LatencyMonitorScreen.kt# Pantalla principal de monitoreo en tiempo real
│   │   └── ApLocatorScreen.kt     # Pantalla declarativa de AP Locator (SELECT_AP, SCANNING, RESULT)
│   ├── components/
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
    └── ApLocatorViewModel.kt       # ViewModel del módulo AP Locator
```

---

## ⚙️ Reglas Arquitectónicas y Convenciones de Desarrollo

### 1. Inmutabilidad y Funciones Puras en Motores
- Todos los motores de análisis (`RssiDirectionAnalyzer`, `ConfidenceCalculator`, `PeriodAnalyzer`, `NetworkDiagnosisEngine`, `LightWeightClassifier`) **MUST BE** objetos puros sin dependencias de Android SDK (`Context`, `View`, `SensorManager`).
- Esto permite ejecutar pruebas unitarias con JUnit 4 rápido sin emulador.

### 2. No Romper APIs Deprecadas de Compatibilidad
- `WifiManager.connectionInfo` y `ScanResult.SSID` están anotados con `@Suppress("DEPRECATION")` intencionalmente para mantener compatibilidad desde `minSdk 26`. NO refactorizar ni eliminar estas llamadas a menos que sea explícitamente solicitado.

### 3. Concurrencia y Reactividad
- Usar `SharedFlow` / `StateFlow` para emitir datos telemétricos desde `NetworkMonitorService`.
- Evitar operaciones de E/S o pings en el hilo principal (`Dispatchers.IO`).
- Usar `SupervisorJob` en corrutinas de servicio para resiliencia ante fallas puntuales de red.

### 4. Honestidad Técnica en AP Locator
- **NUNCA** afirmar que la funcionalidad es un sistema de *Direction of Arrival (DoA)* ni que conoce el *ángulo físico real*.
- Usar siempre términos: *Dirección estimada*, *Zona probable*, *Confianza de estimación*.

### 5. Estilo Visual y Tema
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
