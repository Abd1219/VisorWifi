# Auditoría corregida — Speed Test Fase 1

**Fecha:** 20 de noviembre de 2026
**Alcance:** corrección del motor autónomo de Speed Test y pruebas JVM/HTTP local. Monitor, diagnóstico, PDF, `ReportData`, UI, navegación y conectividad Android no fueron modificados.

## Dictamen

**Aprobado con observaciones para continuar la integración de Fase 1.**

La medición y la cancelación ya tienen cobertura reproducible en JVM y con un servidor HTTP local. Aún no existe una prueba contra una instancia LibreSpeed propia; por ello este dictamen no equivale a aprobar una integración de producción con servidores LibreSpeed externos.

## Hallazgos originales y correcciones

| Hallazgo original | Corrección implementada | Estado |
|---|---|---|
| El validador creaba un cliente HTTP distinto, fuera de `cancel()`. | `LibreSpeedServerValidator.requireAccessible` recibe el mismo `LibreSpeedClient` del motor. Todas las solicitudes activas pasan por su única referencia cancelable. | Corregido. |
| Ping absorbía `IOException` y podía iniciar más solicitudes tras cancelación. | Comprueba `ensureActive()` antes de cada ping, después de la lectura y dentro del `catch` de `IOException`. Una cancelación se propaga y no se transforma en fallo de ping. | Corregido. |
| Una descarga con EOF temprano daba Mbps final válido. | EOF antes de completar `phaseDurationMillis` emite una muestra diagnóstica sin throughput y lanza `IncompleteDownloadException`; `downloadMbps` queda nulo y el resultado global es `FAILED`. | Corregido. |
| La validación transfería datos de descarga/subida antes de medir y no dejaba traza. | La validación es ahora sólo sintaxis/protocolo + un GET ligero de `pingUrl`. El servidor resultante queda `ACCESSIBLE`; sólo una prueba completa finalizada queda `COMPATIBLE`. | Corregido. |
| No había transporte HTTP real en tests. | Se añadió un servidor HTTP local controlado y pruebas de ping, 500, EOF, upload chunked 2xx, upload rechazado 413 y cancelación. | Corregido parcialmente; faltan TLS y LibreSpeed real. |

## Archivos modificados o creados

- `app/src/main/java/com/abdapps/visorwifi/speedtest/domain/SpeedTestEngine.kt`
- `app/src/main/java/com/abdapps/visorwifi/speedtest/domain/SpeedTestServer.kt`
- `app/src/main/java/com/abdapps/visorwifi/speedtest/librespeed/LibreSpeedClient.kt`
- `app/src/main/java/com/abdapps/visorwifi/speedtest/librespeed/LibreSpeedServerValidator.kt`
- `app/src/test/java/com/abdapps/visorwifi/speedtest/SpeedTestEngineTest.kt`
- `app/src/test/java/com/abdapps/visorwifi/speedtest/LibreSpeedClientTransportTest.kt` (nuevo)
- `AUDITORIA_SPEED_TEST_FASE1_CORREGIDA.md` (este informe)

No se agregaron dependencias: el servidor de pruebas utiliza `com.sun.net.httpserver.HttpServer`, disponible en la JVM de las pruebas unitarias y no empaquetado en la app Android.

## Política de cancelación

1. El motor admite una única ejecución hasta que su `Job` termina por completo; no inicia otra mientras una E/S cancelada se está desenrollando.
2. `cancel()` desconecta la conexión activa del cliente común y cancela el `Job`.
3. Ping verifica cancelación cooperativa antes y después de la E/S, y al capturar un error de socket causado por `disconnect()`.
4. Descarga y subida verifican cancelación en cada iteración y cierran streams/conexiones mediante `use` y `finally`.
5. Si una implementación de `HttpURLConnection` devuelve un error de E/S al cancelar en vez de interrumpir inmediatamente, la intención de cancelación prevalece en el estado terminal: no se publica `COMPLETED`.

La finalización de una lectura bloqueante depende todavía de que `HttpURLConnection.disconnect()` o el timeout del transporte desbloqueen la llamada. El motor no inicia una nueva prueba durante ese intervalo.

## Política de EOF anticipado

La prueba de descarga exige que la ventana configurada complete. Si `read()` devuelve EOF antes:

- los bytes ya leídos se conservan sólo en las muestras emitidas para diagnóstico;
- se emite una muestra `DOWNLOADING` con `throughputMbps = null` y error explícito;
- se lanza `IncompleteDownloadException`;
- no se asigna `downloadMbps` y el resultado final es `FAILED`.

No se usa `Content-Length` como reemplazo de bytes ni se inventan bytes. Un endpoint de longitud finita puede ser correcto para otro contrato, pero sólo habilita un ciclo sucesivo cuando declara un tamaño esperado verificable.

## Revisión del protocolo de descarga LibreSpeed

La política anterior trataba todo EOF anterior a la ventana como fallo. Era demasiado estricta para `garbage.php`: el backend oficial genera exactamente `ckSize` bloques de 1 MiB y termina normalmente; el worker oficial vuelve a crear el stream cuando recibe `onload`, manteniendo activa la fase hasta alcanzar su duración objetivo.

### Estrategia implementada

La descarga sigue siendo de **un solo flujo simultáneo**, pero ahora opera en ciclos secuenciales:

1. Comienza una solicitud `GET` a `downloadUrl` con `r` anti-caché y `ckSize`.
2. Cuenta exclusivamente los bytes devueltos por cada `read`.
3. Si la ventana de medición se completa mientras la respuesta sigue abierta, cierra esa respuesta y finaliza la fase.
4. Si llega EOF antes de completar la ventana, sólo se considera una finalización normal si el número real de bytes de esa respuesta coincide con `expectedDownloadResponseBytes` configurado.
5. Una respuesta finita verificada se cierra por completo y se abre una nueva solicitud, sin concurrencia ni reutilización de bytes.
6. EOF sin tamaño de respuesta verificable, o con cantidad distinta a la esperada, es `IncompleteDownloadException`: los bytes quedan como diagnóstico y no se genera `downloadMbps`.

El valor de producción por defecto declara el contrato oficial: `ckSize=100` y `expectedDownloadResponseBytes=100 × 1_048_576` bytes. Los endpoints personalizados deben declarar un tamaño esperado para permitir ciclos; con `null`, un EOF antes de completar la ventana sigue siendo incompleto. `Content-Length` no se usa para calcular velocidad ni para inventar bytes.

### Política de tiempo y bytes

- El tiempo efectivo comienza justo antes de abrir la primera solicitud HTTP.
- Se suma únicamente el tiempo de vida de cada solicitud activa (conexión, cabeceras y lectura). Este es throughput HTTP de aplicación, no velocidad de capa física.
- Cualquier hueco entre cerrar una respuesta y abrir la siguiente queda excluido explícitamente de `activeNanos`, por lo que no reduce artificialmente Mbps ni se presenta como transferencia.
- La fórmula no cambia: `bytes realmente leídos × 8 / segundos efectivos / 1_000_000`.
- Un error HTTP, una lectura interrumpida, un timeout o una cancelación terminan la fase; no se reinicia silenciosamente la solicitud fallida.
- Antes de cada ciclo y cada lectura se comprueba cancelación. Cada stream/conexión se libera mediante `use` antes de iniciar el ciclo siguiente; tras cancelar no se inicia uno nuevo.

Esta conducta corresponde al reinicio de streams finitos del worker oficial, pero conserva una semántica más auditable: no aplica multistream, no aplica compensación de overhead y exige un tamaño de recurso conocido para aceptar EOF temprano.

## Semántica de validación y tráfico

`ServerValidationStatus` ahora expresa:

- `NOT_VALIDATED`: no hubo validación.
- `ACCESSIBLE`: URL/protocolo válidos y `pingUrl` respondió HTTP 2xx.
- `COMPATIBLE`: ping, descarga y subida de la prueba completa terminaron correctamente.
- `VALIDATION_ERROR`: la validación falló antes de acreditar accesibilidad.
- `INCOMPATIBLE`: reservado para una clasificación explícita futura; no se infiere sólo desde accesibilidad.

La validación consume una solicitud `GET` vacía a `empty.php` o endpoint equivalente. El coste de cuerpo esperado es cero o despreciable; cabeceras/TLS siguen generando tráfico de control. Ya no se hace GET a `garbage.php` ni POST de prueba antes de las métricas. Los bytes y tiempo de validación no se mezclan con las fases de medición.

## Semántica de subida

La subida genera un buffer reutilizable de 8 KiB, usa `Transfer-Encoding: chunked` mediante `setChunkedStreamingMode` y contabiliza únicamente bytes aceptados por `OutputStream.write` de la pila HTTP local. No afirma confirmación remota por byte.

El 2xx final acredita que servidor/proxy aceptó la solicitud HTTP completa, pero no demuestra procesamiento aplicativo de cada byte. Un fallo de escritura, cierre, timeout o 4xx/5xx no produce `uploadMbps`; si la descarga previa fue válida se conserva como dato parcial, pero el estado global es `FAILED`.

## Compatibilidad LibreSpeed

La implementación conserva el contrato de LibreSpeed donde es posible:

- Ping: GET a `empty.php` (2xx, normalmente cuerpo vacío).
- Descarga: GET a `garbage.php` con `r` anti-caché y `ckSize=100`, valor aceptado por el backend oficial (MiB).
- Upload: POST a `empty.php` y aceptación 2xx. El backend oficial acepta POST, pero un proxy o servidor de terceros puede rechazar transferencias chunked; se valida en la ejecución real, no se presume.
- Un flujo: se mantiene intencionalmente un único flujo; no pretende equivaler a la configuración multistream del worker web oficial.

La prueba usa 10 s por defecto para cada fase. LibreSpeed recomienda al menos 5 s para descarga y 10 s para subida. No se modificaron esos valores.

Referencias: [documentación](https://github.com/librespeed/speedtest/blob/master/doc.md), [worker](https://github.com/librespeed/speedtest/blob/master/speedtest_worker.js), [`empty.php`](https://github.com/librespeed/speedtest/blob/master/backend/empty.php) y [`garbage.php`](https://github.com/librespeed/speedtest/blob/master/backend/garbage.php).

## Pruebas nuevas

### A. Verificado con pruebas JVM

- Cálculo decimal de Mbps, duración inválida y jitter.
- Una sola ejecución concurrente por motor.
- Resultado `CANCELLED` y ausencia de throughput ficticio ante fallo del cliente.
- Estado `COMPATIBLE` sólo tras completar todas las fases.

### B. Verificado con servidor HTTP local

`LibreSpeedClientTransportTest` levanta un servidor local sin Internet y verifica:

- ping HTTP 200 y error HTTP 500 contabilizado como fallo;
- EOF después de bytes de descarga: `IncompleteDownloadException`, sin Mbps de descarga;
- POST chunked aceptado (2xx) con throughput medido;
- POST rechazado (413 / interrupción de escritura): sin throughput válido;
- cancelación durante validación HTTP bloqueada: resultado `CANCELLED`, nunca `COMPLETED`;
- cancelación durante descarga bloqueada: resultado `CANCELLED`, nunca `COMPLETED`.
- respuesta finita correcta que obliga a abrir varias solicitudes sucesivas hasta completar la ventana;
- bytes contados una sola vez por respuesta (la prueba verifica más de un ciclo y throughput positivo sólo a partir de bytes leídos);
- HTTP 500 en una solicitud sucesiva: error sin Mbps final de descarga;
- timeout en una solicitud sucesiva: error sin Mbps final de descarga.

### C. Pendiente contra una instancia LibreSpeed propia

- TLS con certificado válido e inválido.
- Comportamiento de un servidor PHP/LibreSpeed real ante `Transfer-Encoding: chunked`.
- Límites de tamaño de POST (incluido 413 real), compresión/intermediarios y cierre de socket real.
- Duración sostenida de `garbage.php` para confirmar que 100 MiB cubre la ventana elegida en el hardware/red objetivo.
- Cancelación precisamente en el microintervalo entre dos conexiones sucesivas; el código la verifica antes de cada ciclo, pero ese punto debe estresarse contra una instancia real.
- Cancelación durante una subida bloqueada por el servidor real y timeout de conexión/lectura controlado.

## Limitaciones pendientes

No se configuró ni se inventó un servidor público. La validación definitiva debe levantar una instancia propia de LibreSpeed con `backend/empty.php` y `backend/garbage.php`, HTTPS válido cuando proceda y límites de POST acordes a la carga esperada. Las pruebas JVM deben mantenerse sin Internet.

## Verificación final

```bash
bash ./gradlew :app:testDebugUnitTest
git diff --check
```

Resultados:

- `:app:testDebugUnitTest`: **BUILD SUCCESSFUL** (61 pruebas).
- `git diff --check`: sin errores de espacios.

Confirmación histórica de Fase 1: `NetworkMonitorService`, `NetworkDiagnosisEngine`, pantallas Compose, navegación, `ReportData` y `PdfGenerator` permanecieron intactos durante esa fase. La Fase 2 se documenta posteriormente en `AUDITORIA_CONNECTIVITY_FASE2.md`; tampoco modifica esos componentes ni el motor LibreSpeed de Fase 1.
