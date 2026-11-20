# Auditoría — Conectividad Android / Speed Test Fase 2

**Fecha:** 20 de noviembre de 2026
**Alcance:** proveedor independiente de conectividad para preparar la observación antes, durante y después de una futura prueba de velocidad. No se implementó UI ni se conectó el proveedor al motor LibreSpeed.

## Resultado

Se añadió `speedtest/connectivity`, aislado del monitor existente:

- `NetworkSnapshot` y `WifiLinkInfo`: modelos inmutables sin dependencia de Android.
- `ConnectivityChangeDetector`: comparación pura de dos snapshots; expone pérdida/restauración, cambio de transporte efectivo, VPN, Internet/validación y red subyacente.
- `AndroidConnectivitySnapshotProvider`: adaptador de `ConnectivityManager`, `NetworkCapabilities`, `NetworkCallback`, corrutinas y `Flow`.
- Pruebas JVM para el contrato de modelos y comparación, y una prueba instrumentada de humo para el adaptador Android real.

El callback se registra al empezar a colectar `observe()` y se anula en `awaitClose`; cada colector posee su callback, por lo que no hay registro retenido tras cancelar la colección ni se comparte un callback global duplicable.

## Modelo y ruta efectiva

`effectiveTransport` describe la red por defecto que emplea la aplicación: `WIFI`, `CELLULAR`, `VPN`, `OTHER` o `NONE`. `underlyingTransports` describe los transportes físicos que Android reporta en esas capacidades. `hasWifi`, `hasCellular` e `isVpn` permanecen separados para no afirmar que una VPN sustituye el portador físico.

`networkHandle` sólo se rellena desde API 29. Es un identificador opaco de proceso para comparación de snapshots, no se persiste, no se muestra y no corresponde a SSID/BSSID. En API 26–28 es nulo.

Android no publica una asociación fiable entre una red VPN por defecto y un Wi‑Fi o celular subyacente concretos. Por ello, si las capacidades de la VPN sólo anuncian `TRANSPORT_VPN`, `underlyingTransports` queda vacío: el proveedor no inspecciona `getAllNetworks()` ni infiere un candidato. El detector está preparado para comparar información subyacente cuando esté disponible, pero no promete detectar un cambio físico oculto detrás de una VPN.

## Wi‑Fi, permisos y red móvil

El proveedor no altera `WifiInfoProvider`, el cual sigue atendiendo al monitor/AP Locator e incluye consultas de escaneo. El nuevo adaptador tiene una lectura específica, sin escaneo, para expresar con precisión `WifiInfoAvailability`:

- `AVAILABLE`: Android devolvió el enlace; SSID, BSSID, RSSI, frecuencia y velocidad siguen siendo nullable si el sistema no los devuelve.
- `PERMISSION_OR_SYSTEM_UNAVAILABLE`: falta ubicación fina/gruesa en tiempo de ejecución o el framework rechaza/no entrega `WifiInfo`.
- `NOT_ON_WIFI`: no hay Wi‑Fi en los transportes que reporta la red efectiva.

No se añadieron permisos. Ya existen `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`. No se solicita escaneo ni `READ_PHONE_STATE`. La presencia de `TRANSPORT_CELLULAR` se obtiene de `NetworkCapabilities` con `ACCESS_NETWORK_STATE`; por diseño no se inventa generación radio (`4G`, `LTE`, `5G`) ni se consulta telephony.

`NET_CAPABILITY_INTERNET` y `NET_CAPABILITY_VALIDATED` se reportan por separado. El portal cautivo se expone mediante `NET_CAPABILITY_CAPTIVE_PORTAL`; el estado es nullable en el modelo para futuros adaptadores donde no aplique, aunque el adaptador actual entrega el booleano de capacidades. Uso medido se deriva de la ausencia de `NET_CAPABILITY_NOT_METERED`.

## Compatibilidad

- `minSdk 26`: `registerDefaultNetworkCallback` y las capacidades usadas están disponibles.
- API 29+: se obtiene `Network.networkHandle` opaco; versiones inferiores no intentan fabricarlo.
- SSID/BSSID dependen además de las restricciones de ubicación y del OEM. Valores desconocidos (`<unknown ssid>`, BSSID de marcador, RSSI sentinela) no se convierten en datos ficticios.

## Pruebas

Las pruebas JVM (`ConnectivityChangeDetectorTest`) cubren Wi‑Fi, celular, VPN sobre Wi‑Fi, VPN sobre celular, ausencia de red, Internet sin validar, pérdida, cambio de transporte, cambio subyacente representado por el modelo y Wi‑Fi sin permisos. Son pruebas del contrato puro: no se presentan como una emulación de `ConnectivityManager`.

La colección JVM completa termina con **73 pruebas, 0 fallos** (incluye las 12 nuevas de conectividad); la prueba de cancelación verifica que el registro se anula una vez al terminar la colección.

La prueba instrumentada `AndroidConnectivitySnapshotProviderInstrumentedTest` instancia el proveedor con el framework de un dispositivo/emulador, toma una fotografía y comienza/cancela una observación (`first()`), ejercitando el cierre de callback. Se ejecuta con:

```bash
bash ./gradlew :app:connectedDebugAndroidTest
```

Para verificar cambios reales se requiere controlar un dispositivo: activar/desactivar Wi‑Fi, datos y VPN, revocar ubicación y pasar por un portal cautivo. La asociación de VPN a un portador físico y cambios internos invisibles tras la VPN no son verificables ni deducibles correctamente con la API pública.

## Verificación y alcance preservado

```bash
bash ./gradlew :app:testDebugUnitTest
git diff --check
```

No se modificaron `NetworkMonitorService`, `NetworkDiagnosisEngine`, pantallas Compose, navegación, `ReportData`, `PdfGenerator` ni el motor/cliente LibreSpeed de Fase 1. No se avanzó a Fase 3: todavía no hay cancelación automática, UI, Room, PDF, selección de servidores ni multistream.
