# Auditoría arquitectónica: integración de Speed Test en VisorWifi

**Fecha de auditoría:** 19 de noviembre de 2026
**Alcance:** inspección de solo lectura. No se modificaron clases, dependencias, Gradle, base de datos ni configuración.

## 1. Resumen ejecutivo

VisorWifi tiene una base adecuada para integrar un Speed Test profesional. Tras esta auditoría se implementaron las Fases 1 y 2: existe un motor LibreSpeed autónomo y una capa de conectividad independiente del transporte. Aún no hay UI de Speed Test ni persistencia.

Hallazgos principales:

- El monitor realiza sondeos ICMP LAN/WAN a 1 Hz y conserva una sonda TCP complementaria separada. Esta semántica debe preservarse.
- No existe Room, SQLite, DAO, repositorio, migración ni historial persistente.
- El proveedor de Fase 2 usa `ConnectivityManager`, `NetworkCallback` y `NetworkCapabilities` para fotografiar y observar la red efectiva, su validación y cambios. La observación todavía no está conectada al motor para cancelar una prueba automáticamente.
- El motor de diagnóstico actual es determinista, puro y reutilizable, pero sólo recibe métricas agregadas de latencia/RSSI.
- El PDF ya tiene una separación razonable entre cálculo, presentación y evidencia gráfica; puede recibir resultados Speed Test consolidados.
- La integración debe ser un motor independiente, no una extensión de `NetworkMonitorService`.

## 2. Arquitectura real encontrada

| Capa | Implementación actual | Observación |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Navegación local con `AppScreen`; no usa Navigation Compose. |
| Presentación | `NetworkDiagnosisViewModel`, `ApLocatorViewModel` | Parte del estado del monitor permanece en el composable. |
| Servicio | `NetworkMonitorService` foreground | Bucle a 1 Hz e historial RAM de 600 puntos. |
| Lógica | `NetworkDiagnosisEngine`, `PeriodAnalyzer`, `PeriodStatusEvaluator` | Motores puros y testeables. |
| Sistema | `WifiManager`, `WifiInfo`, `dhcpInfo`, sensores, `AndroidConnectivitySnapshotProvider` | El monitor conserva su foco Wi-Fi; Fase 2 añade contexto general aislado para Speed Test. |
| Datos | Modelos Kotlin inmutables | Sin persistencia. |
| Reporte | `PdfGenerator` | PDF A4 de tres páginas y gráficas off-screen. |

No hay inyección de dependencias. Los motores y proveedores se exponen principalmente como `object`, y `MainActivity` enlaza de forma directa con el servicio. El acoplamiento principal es:

```text
MainActivity -> NetworkMonitorService -> LatencyMonitorScreen
```

## 3. Hallazgos por componente

| Archivo / componente | Función actual comprobada | Limitación | Recomendación futura |
|---|---|---|---|
| `NetworkMonitorService.kt` | Lee `WifiManager.connectionInfo`, mide ICMP a gateway y destinos WAN; emite `SharedFlow`. | Sólo modela Wi-Fi; el gateway IPv4 usa `dhcpInfo` y tiene fallback fijo. | Mantenerlo como monitor Wi-Fi e introducir una fuente de contexto de red independiente. |
| `WifiInfoProvider.kt` | Obtiene SSID/BSSID, RSSI, frecuencia y PHY; enumera resultados de escaneo existentes. | No representa datos móviles, VPN ni solicita escaneos. | Reutilizarlo sólo como parte Wi-Fi de un `NetworkSnapshotProvider`. |
| `speedtest/connectivity/*` | Snapshot inmutable y observación de red efectiva, Internet, validación, uso medido, portal cautivo y Wi-Fi disponible. | No asocia de forma fiable una VPN con un portador físico concreto usando API pública. | Conectar la comparación al flujo de Speed Test en una fase posterior. |
| `LatencyMonitorScreen.kt` | Consume flujos, solicita permisos, calcula presentación y arma `ReportData`. | Combina estado visual, permisos, colecta y armado de reporte. | Crear pantalla y ViewModel Speed Test independientes. |
| `NetworkDiagnosisViewModel.kt` | Calcula métricas agregadas y llama al motor heurístico. | Está limitado a la ventana ICMP/RSSI. | Mantenerlo sin mezclar throughput HTTP. |
| `NetworkDiagnosisEngine.kt` | Clasifica salud, problema ISP, señal/interferencia o jitter. | No conoce servidor de prueba, transporte ni velocidad efectiva. | Añadir motor correlacionado separado en una fase posterior. |
| `PdfGenerator.kt` | Presenta métricas y evidencia ya consolidada. | `ReportData` no contiene resultados de Speed Test. | Recibir bloque opcional consolidado, sin recalcular. |

## 4. Monitor de conectividad actual

El servicio mide cada segundo:

- Latencia ICMP LAN contra la puerta de enlace DHCP.
- Latencia ICMP WAN contra `8.8.8.8`; usa `1.1.1.1:53` como alternativa TCP.
- Latencia TCP complementaria si ICMP falla.
- SSID, BSSID, RSSI, frecuencia, link speed y TX/RX speed Wi-Fi.
- Cambios de SSID y BSSID como eventos de roaming.

La lista de telemetría vive sólo en memoria y está acotada a 600 muestras. Los eventos de roaming están acotados a 100. El monitor no detecta mediante callbacks del sistema el transporte activo, la red validada, VPN ni cambios de red; ese comportamiento se mantiene para no alterar su contrato. `AndroidConnectivitySnapshotProvider` lo cubre de forma independiente y aún no consume sus resultados el monitor.

**Conclusión:** ICMP, TCP complementario, PHY Wi-Fi y transferencia HTTP de Speed Test deben continuar en campos, históricos y cálculos distintos.

## 5. Motor de diagnóstico

`NetworkDiagnosisEngine` evalúa `NetworkMetrics` con reglas deterministas:

1. Insuficiencia de respuestas ICMP.
2. LAN estable + WAN degradada/pérdida: posible incidencia fuera de LAN.
3. Picos LAN + RSSI débil: señal/interferencia.
4. Jitter elevado con RSSI adecuado: inestabilidad.
5. Red saludable.

La introducción de descarga y subida en `NetworkMetrics` sería un error arquitectónico: una transferencia HTTP limitada depende de servidor, protocolo, concurrencia y duración; no equivale a una sonda ICMP continua.

### Recomendación

Conservar `NetworkDiagnosisEngine` y crear después un `CorrelatedDiagnosisEngine` que reciba, por separado:

- Periodo de monitor ICMP/Wi-Fi.
- Resultado Speed Test.
- Snapshot de red al inicio y final.
- Identidad y condiciones del servidor.

Su salida debe separar explícitamente:

- **Problemas observados**: mediciones directas.
- **Posibles causas**: hipótesis basadas en correlación.
- **Causas confirmadas**: sólo cuando el contexto controlado lo permite.

## 6. Persistencia y Room

No se encontraron referencias a Room, SQLite, entidades, DAO, repositorios, bases de datos ni migraciones.

Por consecuencia:

- No existe versión de base de datos.
- No existen datos que migrar.
- El historial no sobrevive al reinicio del proceso.
- No hay estrategia actual de exportación o retención persistente.

La primera persistencia debe ser aditiva, introduciendo tablas de Speed Test sin modificar `LatencyPoint` ni simular una migración de datos que no existen:

- `speed_test_results`
- `speed_test_samples`
- `speed_test_servers`

## 7. Reportes PDF

El `PdfGenerator` genera tres páginas:

1. Resumen ejecutivo y diagnóstico.
2. Métricas LAN, WAN, Wi-Fi y TCP complementario.
3. Evidencia gráfica, eventos, observaciones y firmas.

Fortalezas:

- `ReportData` recibe datos consolidados.
- Las sondas TCP no sustituyen ICMP.
- La velocidad PHY se presenta como enlace Wi-Fi, no como velocidad Internet.
- Las gráficas se renderizan off-screen y conservan relación de aspecto.
- `PeriodAnalyzer` y la narrativa se mantienen fuera de la capa de dibujo.

Para la integración futura, el reporte debe recibir un bloque opcional `SpeedTestReportSection` con resultado ya calculado, servidor, red usada, condición VPN, duración y advertencias. No debe recalcular bytes, velocidades ni diagnóstico.

## 8. Interfaz y experiencia

La interfaz actual usa Dark Mode Neo-Brutalista, cian como color principal y estados semánticos verdes/rojos. La pantalla Monitor tiene modos Resumen y Técnica; el selector sólo altera la presentación local.

La nueva pantalla debe conservar esa identidad y mostrar con claridad:

- Servidor elegido y si es público/privado.
- Transporte efectivo: Wi-Fi, móvil, VPN u otro.
- Estado de red: validada, sin Internet, portal cautivo o cambiada.
- Fase activa: preparación, ping, descarga, subida, finalización.
- Bytes y tiempo reales transferidos.
- Cancelación segura y error comprensible.

## 9. Arquitectura futura recomendada

```text
ConnectivitySnapshotProvider ─┐
WifiInfoProvider ─────────────┼─> NetworkSnapshot
                              │
Monitor Engine ─> MonitorPeriod ─┐
                                 ├─> CorrelatedDiagnosisEngine ─> Diagnosis
SpeedTest Engine ─> SpeedTestResult ┘
             │
             └─> SpeedTestRepository (fase posterior)

ReportAssembler (sin cálculos) ─> UnifiedReportData ─> PdfGenerator
```

Paquetes propuestos, sin exigir todavía módulos Gradle separados:

```text
speedtest/
  domain/
    SpeedTestEngine.kt
    SpeedTestResult.kt
    SpeedTestSample.kt
    SpeedTestServer.kt
    SpeedTestStatus.kt
    NetworkSnapshot.kt
  librespeed/
    LibreSpeedClient.kt
    LibreSpeedServerValidator.kt
  connectivity/
    ConnectivitySnapshotProvider.kt
  presentation/
    SpeedTestViewModel.kt
    SpeedTestScreen.kt
  data/                         # fase de persistencia
    SpeedTestRepository.kt
    local/
```

## 10. Contratos de dominio propuestos

### `SpeedTestServer`

Debe contener identificador, nombre, URL base, rutas de ping/download/upload, tipo (`PUBLIC` o `PRIVATE`), protocolo, ubicación declarada y fecha/resultado de validación.

### `NetworkSnapshot`

Debe conservar timestamp, transporte (`WIFI`, `CELLULAR`, `VPN`, `OTHER`), estado validado/cautivo, red medida, atributos Wi-Fi disponibles, tipo de red móvil cuando el sistema lo exponga y metering.

### `SpeedTestSample`

Muestra temporal con fase, timestamp monotónico, bytes acumulados, throughput calculado y error opcional.

### `SpeedTestResult`

Resultado inmutable con inicio/fin, servidor inmutable, snapshot inicial/final, descarga, subida, latencia HTTP, jitter HTTP, pérdida de sondas HTTP, estado terminal, razón de cancelación/error y detección de cambio de red.

### `SpeedTestStatus`

Estados sugeridos:

```text
IDLE -> PREPARING -> PINGING -> DOWNLOADING -> UPLOADING -> FINALIZING -> COMPLETED
                                                └-> CANCELLED / FAILED / NETWORK_CHANGED
```

Las velocidades deben calcularse exclusivamente con bytes transferidos y tiempo monotónico efectivo.

## 11. LibreSpeed e iperf3

LibreSpeed es un candidato adecuado como primer adaptador HTTP. Su descarga utiliza datos de prueba y usa endpoints vacíos para upload/ping. Su ping/jitter es HTTP y no ICMP; debe modelarse y etiquetarse como tal.

Recomendaciones:

- Priorizar un servidor propio LibreSpeed para resultados repetibles.
- Tratar servidores públicos como catálogo validable en tiempo de uso, no como dependencia fija.
- Permitir servidores privados con HTTPS, validación de endpoint, timeout, límite de redirecciones y validación de respuesta.
- Seleccionar automáticamente por disponibilidad y sonda inicial; no afirmar que cercanía geográfica implique mejor servidor.
- Diseñar `Iperf3Engine` como motor adicional independiente en el futuro, no como variación de LibreSpeed.

## 12. Riesgos técnicos y compatibilidad Android

- SSID/BSSID y `WifiInfo` pueden requerir permisos y estado de ubicación apropiados.
- Una VPN puede ser el transporte efectivo aunque la red subyacente sea Wi-Fi o móvil.
- Una red conectada puede no tener Internet: portal cautivo o falta de validación.
- Si la red cambia durante una prueba, el resultado debe cancelarse o marcarse como interrumpido; nunca se deben combinar bytes de ambas redes.
- Las pruebas consumen batería y, en red móvil, datos del usuario.
- La velocidad depende de servidor, TLS, congestión, CDN, concurrencia y operador; no demuestra por sí sola la capacidad contractual del ISP.
- La pérdida observada por HTTP describe intentos y respuestas hacia ese servidor; no equivale a pérdida IP o ICMP universal.

## 13. Plan de implementación

| Fase | Alcance | Archivos futuros principales | Criterio de aceptación |
|---|---|---|---|
| 0 | Auditoría y contratos | Documentación/modelos de diseño | Sin cambios funcionales; contratos aprobados. |
| 1 | Motor puro LibreSpeed y pruebas JVM | `speedtest/domain/*`, `speedtest/librespeed/*` | Download/upload con bytes reales; timeout, cancelación y errores probados. |
| 2 | Contexto Wi-Fi/móvil/VPN | `speedtest/connectivity/*` | **Completada:** snapshot identifica transporte, validación y cambio de red; limitaciones VPN documentadas. |
| 3 | UI Speed Test | `SpeedTestViewModel`, `SpeedTestScreen`, `MainActivity` | **Integrada y bloqueada con honestidad:** navegación, estados y conectividad listos; falta servidor LibreSpeed aprobado para tráfico real. |
| 4 | Historial | Room entities/DAO/repository | Resultados sobreviven reinicio y pueden consultarse. |
| 5 | Diagnóstico correlacionado | `CorrelatedDiagnosisEngine` | Separa observado/posible/confirmado sin causalidad infundada. |
| 6 | PDF unificado | ensamblador, `ReportData`, `PdfGenerator` | Incluye resultado, servidor y contexto sin alterar métricas actuales. |

## 14. Dependencias futuras

No se requieren dependencias para la auditoría. Para implementar:

- Un cliente HTTP único con streaming, TLS y cancelación, preferiblemente OkHttp.
- Room (`room-runtime`, `room-ktx` y compilador KSP) sólo en la fase de historial.
- No se recomienda una librería Speed Test opaca: los cálculos y el protocolo deben ser auditables.
- No se necesita una dependencia adicional para `ConnectivityManager`.

## 15. Referencias externas

- [Android: ConnectivityManager.NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)
- [Android: monitor de conectividad y estado de red](https://developer.android.com/training/monitoring-device-state/connectivity-status-type)
- [LibreSpeed: documentación de protocolo](https://github.com/librespeed/speedtest/blob/master/doc.md)

## Conclusión

La ruta segura consiste en crear un Speed Test autónomo, con resultados propios y snapshots de red inmutables, y correlacionarlo posteriormente con el monitor. Esto mantiene la integridad de ICMP, TCP, PHY Wi-Fi, diagnóstico actual y reportes PDF.
