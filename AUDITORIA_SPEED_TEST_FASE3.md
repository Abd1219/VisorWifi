# Auditoría — Speed Test Fase 3

**Fecha:** 20 de noviembre de 2026

**Alcance:** renovación de navegación e integración de interfaz/coordinación de Speed Test. La ejecución HTTP real queda bloqueada explícitamente hasta recibir un servidor LibreSpeed aprobado.

## Resumen ejecutivo

La navegación principal pasó del selector superior Monitor/AP Locator a una barra inferior con cuatro destinos: Monitor, AP Locator, Speed Test y Reportes. Monitor y AP Locator conservan sus contratos; el botón `VOLVER` de AP Locator se retiró porque duplicaba la navegación principal.

Speed Test ya coordina los contratos de Fase 1 y 2, pero no inicia tráfico sin configuración aprobada. La pantalla muestra: **“Servidor no configurado. La prueba real no está disponible”**. No se añadió una URL pública, endpoint, resultado ni velocidad simulada.

## Navegación anterior y nueva

| Antes | Ahora |
|---|---|
| Selector superior `ModeSwitcher`: Monitor / AP Locator. | `NavigationBar` inferior persistente: Monitor / AP Locator / Speed Test / Reportes. |
| AP Locator tenía botón `VOLVER`. | AP Locator usa exclusivamente la barra inferior. |
| Sin destino para pruebas ni reportes. | Speed Test y Reportes están integrados como destinos de primer nivel. |

La selección se conserva con `rememberSaveable`. Los ViewModels permanecen vinculados al `ComponentActivity`; al abandonar AP Locator se detienen sus sensores/polling existentes, y al abandonar Speed Test se cancela exclusivamente su observación de conectividad.

## Destinos implementados

- **Monitor:** sin cambios a servicio, diagnóstico, métricas ni PDF.
- **AP Locator:** conserva sensores, polling independiente y sesión; se eliminó sólo la navegación de retorno redundante.
- **Speed Test:** pantalla independiente con conectividad, fases reales, botón iniciar/cancelar, resultados HTTP y advertencias de cambios de red.
- **Reportes:** estado vacío profesional. No crea registros, Room, historial ni reportes simulados.

## Integración de Speed Test

`SpeedTestViewModel` coordina `SpeedTestRunner` (adaptador de `SpeedTestEngine`), `ConnectivitySnapshotProvider` y `SpeedTestServerSource`:

- Consume los `StateFlow`/`SharedFlow` existentes de estado, muestras y resultados del motor.
- Presenta `PREPARING`, `PINGING`, `DOWNLOADING`, `UPLOADING`, `FINALIZING`, `COMPLETED`, `FAILED` y `CANCELLED` con etiquetas comprensibles. No agrega progreso porcentual que el motor no produce.
- Una vez exista servidor, `CANCELAR PRUEBA` llama a `SpeedTestEngine.cancel()` mediante el adaptador real. El motor sigue impidiendo ejecuciones simultáneas.
- Download, upload, latencia y jitter se etiquetan explícitamente como HTTP; `N/D` sustituye a valores inexistentes, nunca cero.

`NoApprovedSpeedTestServerSource` devuelve `null` intencionalmente. Es el único origen configurado en esta fase, por lo que la acción de inicio queda deshabilitada y no puede generar una medición ficticia. Una fase posterior sólo debe inyectar una implementación de `SpeedTestServerSource` con el `SpeedTestServer` aprobado; no requiere cambiar cliente, motor o pantalla.

## Conectividad y cambios de red

Al mostrar Speed Test se obtiene una fotografía y se inicia `observe()` de `AndroidConnectivitySnapshotProvider`. Al iniciar una prueba configurada se guarda la fotografía inicial; los cambios intermedios y la fotografía final se comparan con `ConnectivityChangeDetector`.

La UI informa pérdida/restauración, transporte efectivo, VPN, validación de Internet y cambios subyacentes reportados. No cancela automáticamente la prueba. VPN y transporte físico se presentan por separado y no se infiere un portador oculto por Android. `DisposableEffect` llama a `onScreenHidden()` y libera la observación al abandonar el destino.

## Diseño visual

La barra inferior usa fondo oscuro, selección cian, etiquetas legibles e insets estándar de `NavigationBar`. Speed Test conserva tarjetas oscuras, cian/magenta para jerarquía, verde sólo para resultados medidos y amarillo para advertencias de conectividad. Reportes usa un estado vacío honesto.

## Archivos creados

- `speedtest/config/SpeedTestServerSource.kt`
- `viewmodel/SpeedTestViewModel.kt`
- `viewmodel/SpeedTestUiState.kt`
- `ui/components/MainBottomNavigation.kt`
- `ui/screens/SpeedTestScreen.kt`
- `ui/screens/ReportsScreen.kt`
- `SpeedTestPhase3PresentationTest.kt`
- `AUDITORIA_SPEED_TEST_FASE3.md`

## Archivos modificados

- `MainActivity.kt`
- `ui/screens/ApLocatorScreen.kt`
- `ui/screens/LatencyMonitorScreen.kt`

## Archivos protegidos sin modificación

No se modificaron `NetworkMonitorService`, `NetworkDiagnosisEngine`, `WifiInfoProvider`, `LibreSpeedClient`, `LibreSpeedServerValidator`, `SpeedTestEngine`, `NetworkSnapshot`, `ConnectivityChangeDetector`, `AndroidConnectivitySnapshotProvider`, `ReportData` ni `PdfGenerator`.

## Pruebas ejecutadas

`bash ./gradlew :app:testDebugUnitTest`, `bash ./gradlew :app:assembleDebug` y `git diff --check` finalizaron correctamente. La suite JVM contiene **77 pruebas, 0 fallos**. Las cuatro pruebas nuevas verifican los destinos de navegación, fuente sin servidor y representación de fases; son contratos de UI puros y no se presentan como mediciones ni conectividad Android real.

No se ejecutaron pruebas instrumentadas. Posteriormente, el APK `debug` ensamblado se instaló correctamente por ADB inalámbrico en un dispositivo físico RMX5085; esa instalación confirma despliegue, no sustituye pruebas instrumentadas ni acredita una medición HTTP real.

## Funcionalidad bloqueada y pruebas reales pendientes

- No puede iniciarse una medición HTTP hasta recibir un `SpeedTestServer` aprobado (base URL, `empty.php`, `garbage.php`, protocolo y política de operación).
- Por lo anterior no hubo prueba real de ping, descarga, subida, cancelación HTTP ni cambios de red durante una ejecución en dispositivo.
- Sigue pendiente probar en dispositivo Wi-Fi, celular, VPN, permisos Wi-Fi/ubicación, portal cautivo, cancelación con E/S bloqueada y liberación del callback contra Android real.

## Pendiente para Fase 4

Proporcionar e inyectar el servidor LibreSpeed aprobado, ejecutar pruebas instrumentadas y de red controlada, y sólo después evaluar las fases de historial/persistencia. No se avanzó a Room, integración PDF, diagnóstico correlacionado, multistream ni cancelación automática por cambio de red.
