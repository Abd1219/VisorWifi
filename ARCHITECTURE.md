# Arquitectura Técnica de VisorWifi (`ARCHITECTURE.md`)

Este documento detalla el diseño de arquitectura, los flujos de datos y los patrones de implementación del proyecto **VisorWifi**.

---

## 1. Visión General de Arquitectura

VisorWifi sigue los principios de **Clean Architecture** adaptados a aplicaciones de diagnóstico en tiempo real:

```
[ Capa de Presentación (UI) ]
  Jetpack Compose + StateFlow + ModeSwitcher
        │
        ▼
[ Capa de ViewModel ]
  NetworkDiagnosisViewModel / ApLocatorViewModel
        │
        ▼
[ Capa de Dominio y Motores ]
  NetworkDiagnosisEngine / HybridDiagnosisEngine / RssiDirectionAnalyzer / ConfidenceCalculator / PdfGenerator
        │
        ▼
[ Capa de Servicios y Proveedores de Datos ]
  NetworkMonitorService (1Hz Flow)  │  WifiInfoProvider (Consulta Desacoplada)
  SensorOrientationManager (Sensores)│  WifiManager & ConnectivityManager
```

---

## 2. Flujo de Datos Telemétricos en Tiempo Real

### 2.1 Flujo por Servicio en Primer Plano (Foreground Service)
```mermaid
sequenceDiagram
    participant OS as Sistema Android (Ping/TCP/WiFi)
    participant NMS as NetworkMonitorService
    participant Flow as latencyFlow (SharedFlow)
    participant VM as ViewModels
    participant UI as Pantallas Compose

    loop Cada 1 Segundo (1Hz)
        OS->>NMS: Lectura de WifiInfo (BSSID, RSSI, freq, speeds)
        NMS->>OS: Ping ICMP / TCP Fallback (Gateway & 8.8.8.8)
        OS-->>NMS: Latencias RTT (ms)
        NMS->>Flow: Emit(LatencyPoint)
        Flow->>VM: Actualizar historial circular (600 pts)
        VM->>VM: Calcular métricas y evaluar diagnóstico / AP Locator
        VM->>UI: Recomposición reactiva por StateFlow
    end
```

### 2.2 Flujo Desacoplado para AP Locator (`WifiInfoProvider`)
Cuando el usuario entra directamente a la pantalla de **AP Locator** sin tener iniciado el servicio de monitoreo en segundo plano, `ApLocatorViewModel` ejecuta un polling directo e independiente mediante `WifiInfoProvider`:
- Consulta `WifiSnapshot` instantáneo cada 1 segundo en una corrutina aislada con `viewModelScope`.
- Obtiene escaneo de puntos de acceso visibles (`getAvailableAps`) sin acoplarse al servicio de latencias.
- Registra muestras atómicas `RssiOrientationSample` durante la calibración 360°.

---

## 3. Módulo AP Locator (Procesamiento de Señal y Orientación)

### 3.1 Adquisición de Sensores (`SensorOrientationManager`)
- **Fusión de Sensores**: Utiliza `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD` enviando matrices a `SensorManager.getRotationMatrix()`. Si el dispositivo carece de magnetómetro, utiliza `TYPE_ROTATION_VECTOR`.
- **Filtro de Paso Bajo (Low-Pass Filter)**: Se aplica $\alpha = 0.25$ sobre la aceleración y vector geomagnético para suprimir vibraciones de mano.
- **Interpolación Circular**: La transición $359^\circ \leftrightarrow 0^\circ$ se interpola manejando la diferencia angular mínima ($\pm 180^\circ$) para evitar rotaciones bruscas de $358^\circ$ en la UI.

### 3.2 Algoritmo de Estimación (`RssiDirectionAnalyzer`)

```mermaid
flowchart TD
    M1[Muestras RssiOrientationSample] --> S1[Ordenar por Timestamp]
    S1 --> S2[Filtro de Mediana W=5]
    S2 --> S3[Media Móvil W=5]
    S3 --> S4[Binning Angular 36 Bins x 10°]
    S4 --> S5[Búsqueda de Zona de Pico -10dBm del Max]
    S5 --> S6["Centroide Ponderado Circular: atan2(Σ w·sin θ, Σ w·cos θ)"]
    S6 --> S7[ConfidenceCalculator 5 Factores]
    S7 --> OUT[DirectionEstimate]
```

### 3.3 Motor de Confianza (`ConfidenceCalculator`)

Factor | Peso (%) | Fórmula / Umbrales
---|---|---
**Cantidad Muestras** | 20% | $0$ si $< 12$; lineal entre $12$ y $36+$
**Rango RSSI** | 25% | $0$ si $< 5\text{ dBm}$; $100\%$ si $\ge 25\text{ dBm}$
**Varianza Pico** | 20% | Desviación estándar en bin de pico ($< 2.0\text{ dBm} \rightarrow 100\%$)
**Cobertura Angular** | 20% | $(\text{Grados Cubiertos} / 360^\circ) \times \text{Densidad Muestras}$
**Consistencia Pico** | 15% | Ancho de zona de pico $\ge 3$ bins y ventaja sobre promedio $\ge 10\text{ dBm}$

---

## 4. Motor de Diagnóstico Híbrido de Red

```
                     ┌──────────────────────────────┐
                     │    NetworkMetrics (10-300s)  │
                     └──────────────┬───────────────┘
                                    │
            ┌───────────────────────┴───────────────────────┐
            ▼                                               ▼
┌────────────────────────┐                     ┌────────────────────────┐
│ NetworkDiagnosisEngine │                     │ LightWeightClassifier  │
│   (Fase 1 Heurística)  │                     │   (Fase 2 IA Local)    │
│   (Reglas rígidas ms)  │                     │   (Puntuación ventana) │
└───────────┬────────────┘                     └───────────┬────────────┘
            │                                               │
            └───────────────────────┬───────────────────────┘
                                    ▼
                       ┌─────────────────────────┐
                       │  HybridDiagnosisEngine  │
                       │   (Fusión e Inferencia) │
                       └────────────┬────────────┘
                                    ▼
                           [DiagnosisState]
```

---

## 5. Motor de Generación de Reportes PDF (`PdfGenerator`)

El módulo `PdfGenerator` renderiza reportes ejecutivos vectoriales de 2 páginas con gráficos embebidos, tablas de métricas y diagnóstico pericial:

- **Sanitización de Glifos y Ligaduras (`sanitizePdfText`)**: El motor Skia PDF de Android carece de glifos para ciertas ligaduras latinas (`fi`, `fl`, `ff`), en-dashes (`–`), superíndices y emojis, lo cual provocaba corrupción tipográfica ("Conanza", "Ocinas", concatenación de timestamps). La sanitización convierte caracteres especiales a representaciones ASCII/Unicode compatibles antes del trazado.
- **Desactivación de Ligaduras en Paint (`disableLigatures`)**: Configura `fontFeatureSettings = "\"liga\" 0, \"clig\" 0, \"dlig\" 0"` en `TextPaint` para prevenir la fusión automática de caracteres en el renderizado.
- **Generación Vectorial y Rasterizado Dinámico**: Los gráficos de latencia y RSSI se convierten en bitmaps de alta resolución para incrustarse nítidamente en el documento PDF de 72 DPI estándar A4.

---

## 6. Estrategia de Pruebas Unitarias

Todas las pruebas unitarias se encuentran en `app/src/test/java/`.

### Suites de Pruebas Principales:
1. **`RssiDirectionAnalyzerTest`**: Verificación de filtros de mediana, medias móviles, binned angular y cálculo de centroide circular.
2. **`ConfidenceCalculatorTest`**: Evaluación de los 5 factores ponderados bajo distintos escenarios de señal y movimiento.
3. **`ApLocatorIndependenceTest`**: Validación de aislamiento e interoperabilidad del AP Locator operando con y sin `NetworkMonitorService`.
4. **`PdfSanitizerTest`**: Pruebas exhaustivas de sanitización tipográfica (ligaduras latinas, guiones en-dash/em-dash, emojis y símbolos matemáticos).
5. **`PeriodAnalyzerTest` & `PeriodStatusEvaluatorTest`**: Cálculo de percentiles, jitter y estados de periodos para reportes.
6. **`HybridDiagnosisEngineTest` & `LightWeightClassifierTest`**: Clasificación determinista de anomalías y resolución de discrepancias heurísticas.

### Regla Fundamental:
Los algoritmos (`RssiDirectionAnalyzer`, `ConfidenceCalculator`, `PeriodAnalyzer`, `LightWeightClassifier`, `PdfGenerator.sanitizePdfText`) deben ser **funciones puras** independientes del framework de Android. Esto permite ejecutar la suite completa de pruebas unitarias en **< 30 segundos** sin requerir Robolectric ni emulador.

### Comando para Ejecución de Pruebas:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew :app:testDebugUnitTest
```
