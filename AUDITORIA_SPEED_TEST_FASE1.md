# Auditoría de integración real — Speed Test Fase 1

**Fecha:** 20 de noviembre de 2026
**Alcance:** revisión estática del motor y sus pruebas JVM. No se modificó código de producción, el monitor, el diagnóstico ni el PDF. No se usó un servidor público arbitrario.

## 1. Resumen ejecutivo

El motor está correctamente aislado y la ruta de datos principal es real: descarga cuerpos HTTP por streaming, cuenta los bytes devueltos por `read`, genera el cuerpo de subida incrementalmente y usa `System.nanoTime` para las velocidades y las latencias. No usa `Content-Length`, no guarda descargas completas en memoria y conserva separados HTTP, ICMP y TCP.

Sin embargo, el dictamen no es una aprobación plena para pruebas reales. Hay dos defectos importantes de cancelación: la validación usa otra instancia de `LibreSpeedClient`, por lo que `SpeedTestEngine.cancel()` no puede cerrar su conexión activa; y el bucle de ping captura `IOException` sin comprobar cancelación, con lo que puede seguir intentando pings tras cancelar. No hay una prueba de integración reproducible contra una instancia LibreSpeed propia ni pruebas de transporte real para descarga incompleta, rechazo de upload, timeout/cancelación durante E/S o recursos cerrados.

**Dictamen: requiere correcciones antes de declarar el motor preparado para producción contra servidores LibreSpeed reales.** El núcleo de medición es adecuado como base, pero los puntos de cancelación y las pruebas de integración son condiciones de aceptación de Fase 1.

## 2. Componentes revisados

| Componente | Revisión |
|---|---|
| `speedtest/domain/SpeedTestEngine.kt` | Orquestación secuencial, flujos, estado terminal y cancelación. |
| `speedtest/domain/SpeedTestMetrics.kt` | Mbps decimal y jitter HTTP. |
| `speedtest/domain/SpeedTestResult.kt`, `SpeedTestSample.kt`, `SpeedTestServer.kt`, `SpeedTestStatus.kt` | Modelos, semántica y separación de transportes. |
| `speedtest/librespeed/LibreSpeedClient.kt` | HTTP, streaming, upload chunked, timeouts, redirecciones y cierres. |
| `speedtest/librespeed/LibreSpeedServerValidator.kt` | Validación sintáctica y solicitudes de disponibilidad. |
| `speedtest/*Test.kt` | Cobertura JVM disponible. |
| `app/build.gradle.kts`, `AndroidManifest.xml` | Dependencias y permiso `INTERNET`. |

## 3. Hallazgos

### Críticos

No se hallaron defectos que mezclen métricas ICMP/TCP/HTTP ni velocidades simuladas en el camino de producción.

### Importantes

1. **La cancelación no cubre la validación del servidor.** `SpeedTestEngine` construye por defecto un `LibreSpeedClient` y un `LibreSpeedServerValidator()` que a su vez construye *otro* cliente. `cancel()` llama sólo a `client.cancelActiveRequest()`. Mientras `requireValid()` ejecuta ping, descarga de un byte o upload de un byte, la conexión activa pertenece al cliente interno del validador y puede esperar hasta `readTimeout` (15 s por defecto). Es reproducible con un endpoint que acepta la conexión y no responde.

2. **Cancelación durante ping no se comprueba dentro del bucle.** `LibreSpeedClient.ping()` atrapa todo `IOException` y continúa con el siguiente `repeat`; no invoca `ensureActive()` ni vuelve a propagar una cancelación. Si `disconnect()` provoca una `SocketException`, puede contarla como fallo y abrir nuevos pings aunque el `Job` ya esté cancelado. Las llamadas bloqueantes posteriores dependen del timeout para terminar.

3. **No hay integración real reproducible.** Los tests sustituyen cliente y validador por dobles (`FakeClient`, `AcceptingValidator`). Demuestran orquestación, no la compatibilidad de `HttpURLConnection` con un backend LibreSpeed, TLS, `Transfer-Encoding: chunked`, códigos HTTP reales o liberación de sockets.

4. **EOF temprano de descarga se acepta como éxito parcial.** Un cuerpo con al menos un byte seguido de EOF produce una velocidad válida, aunque termine antes de `phaseDurationMillis`. Con `garbage.php` oficial y `ckSize=100` se esperan 100 MiB, pero el contrato `SpeedTestServer` permite también una URL genérica. No se detecta ni marca que la transferencia acabó prematuramente o que no pudo sostener la ventana de medida.

5. **Validación consume tráfico y no conserva su resultado.** `requireValid()` descarga al menos un byte y realiza un POST de un byte antes del test, pero `SpeedTestServer.validationStatus` no se actualiza (es inmutable) ni el resultado informa que la validación ocurrió. Esto no falsea las métricas finales, pero tiene coste de red y trazabilidad incompleta.

### Menores

1. La medición de ping incluye desde antes de `responseCode` hasta el primer `read`; para `empty.php` es una definición coherente de RTT HTTP, pero no se documenta en el resultado qué hito exacto representa.
2. La subida no añade el parámetro anti-caché `r` ni `Content-Encoding: identity` que utiliza el worker oficial. `POST` no suele cachearse y el cuerpo binario es poco compresible; no es una incompatibilidad con `empty.php`, pero difiere del cliente de referencia.
3. Un solo stream para descarga y subida es válido, pero no pretende reproducir los valores multistream configurables del worker oficial. El resultado debe presentarse como prueba HTTP de un flujo, no como equivalente numérico directo de la UI web de LibreSpeed.
4. `NETWORK_CHANGED` existe en el enum, pero correctamente no se utiliza todavía: detectar cambios de red corresponde a Fase 2.

## 4. Verificación por área

### Descarga

- **Streaming real:** sí. `BufferedInputStream` consume bloques de `DEFAULT_BUFFER_SIZE`; no crea un archivo, `ByteArrayOutputStream` ni un buffer del cuerpo completo.
- **Bytes efectivos:** sí. Sólo suma el entero devuelto por `input.read(buffer)`; no consulta `Content-Length`.
- **Reloj:** sí. Inicio, muestras y resultado se basan en `clockNanos`, cuyo valor por defecto es `System.nanoTime`.
- **Cálculo:** correcto: `bytes * 8 / segundos / 1_000_000`; no aplica compensación de overhead.
- **Límite:** existe límite temporal superior (`phaseDurationMillis`, 10 s por defecto). Sin embargo, EOF finaliza antes la prueba y se reporta éxito si hubo bytes; véase hallazgo importante 4.
- **Errores HTTP/lectura:** códigos fuera de 2xx lanzan `HttpStatusException`; `IOException` de lectura falla el motor. Falta una prueba de respuesta truncada y una política explícita para EOF anticipado.

### Subida

- **Solicitud real y memoria:** sí. `setChunkedStreamingMode(DEFAULT_BUFFER_SIZE)` y un buffer reutilizado de 8 KiB evitan crear un archivo o cuerpo entero en RAM.
- **Compatibilidad:** el `empty.php` oficial responde `200 OK` para GET/POST y no exige un cuerpo específico. El backend base no rechaza por `Transfer-Encoding: chunked`. Aun así, proxies o despliegues de terceros pueden rechazar chunked; no se debe asumir compatibilidad universal sin validar el servidor concreto.
- **Semántica de bytes:** el comentario es correcto: cada `OutputStream.write` acredita bytes aceptados por la pila local de salida, no una confirmación remota byte a byte. El `2xx` posterior confirma que el servidor/proxy aceptó la solicitud HTTP completa, pero no permite probar que cada byte fue procesado por la aplicación de destino. El modelo no expone esos bytes como “confirmados”, lo cual evita una afirmación falsa.
- **Errores:** un `write`, cierre o código no 2xx lanza error; por tanto no se asigna `uploadMbps`. Si falla después de descarga, el resultado conserva la descarga medida y queda `FAILED`, que es una representación razonable del progreso parcial.

### Ping y jitter HTTP

- Cada éxito ejecuta un `GET` real a `pingUrl`, comprueba 2xx y lee un byte/EOF.
- La latencia mide inicio de la operación hasta recibir/leer la respuesta HTTP; es HTTP, no ICMP. El modelo lo declara expresamente.
- Los `IOException` se cuentan en `failed`; sólo latencias exitosas se usan en mínimo, media, máximo y jitter.
- Fórmula documentada y correcta: media de `abs(latencia[i] - latencia[i-1])`, con al menos dos éxitos.
- Falta distinguir en el resultado errores de ping individuales y cubrir cancelación durante ese bucle.

### Cancelación, concurrencia y recursos

- `start()` está sincronizado y rechaza una segunda ejecución activa: correcto para evitar resultados ambiguos.
- Descarga/subida llaman `ensureActive()` por iteración y cierran stream/conexión con `use`/`finally`: correcto una vez que la lectura/escritura retorna.
- `DisconnectingInputStream` desconecta al cerrar: correcto para las respuestas GET.
- La cancelación del cliente principal llama a `disconnect()` antes de cancelar el Job: adecuado para desbloquear E/S de la transferencia principal.
- Persisten los dos defectos importantes ya descritos: cliente de validación independiente y pings que consumen la excepción de cancelación.
- No hay prueba real ni un test específico que pruebe que no se emite `COMPLETED` tras cancelar durante ping, descarga o upload. El test actual cancela un doble que espera con `delay` durante descarga; no ejercita un socket ni upload/ping.

## 5. Compatibilidad con LibreSpeed

La implementación coincide parcialmente con LibreSpeed oficial:

| Aspecto | Referencia LibreSpeed | Implementación | Evaluación |
|---|---|---|---|
| Ping | `empty.php`, GET, respuesta 200 vacía | GET, 2xx, lee un byte/EOF | Compatible. |
| Descarga | `garbage.php?r=…&ckSize=…`; `ckSize` en MiB, máximo 1024 | GET con `r` y `ckSize=100` | Compatible con el backend oficial. |
| Upload | POST a `empty.php`; worker usa cuerpos con tamaño conocido y comprueba 2xx | POST chunked, comprueba 2xx final | Compatible con el `empty.php` oficial; no garantizado para todo proxy o servidor público. |
| Códigos | Worker trata 2xx como aceptación de upload | Todo endpoint exige 2xx | Compatible. |
| Cuerpo descargado | Worker reinicia streams que finalizan para mantener duración | Un stream y EOF termina la fase | Diferencia importante. |
| Concurrencia | Worker usa varios streams configurables | Un stream | Decisión válida, pero no comparable 1:1 con los resultados UI oficiales. |

La documentación oficial recomienda descarga de al menos 5 s y subida de al menos 10 s. La configuración actual usa 10 s para ambas, por lo que cumple el mínimo de descarga pero no la recomendación de subida si se busca equiparación con ese cliente. La documentación también advierte que el servidor debe admitir POST grandes; esto se debe verificar en la instancia propia.

Fuentes contrastadas: [documentación LibreSpeed](https://github.com/librespeed/speedtest/blob/master/doc.md), [worker de referencia](https://github.com/librespeed/speedtest/blob/master/speedtest_worker.js), [backend `empty.php`](https://github.com/librespeed/speedtest/blob/master/backend/empty.php) y [backend `garbage.php`](https://github.com/librespeed/speedtest/blob/master/backend/garbage.php).

## 6. Riesgos de medición incorrecta

- Una respuesta descargada finita o truncada puede presentarse como velocidad válida de una ventana demasiado corta.
- La velocidad de subida representa entrega a la pila de salida local hasta que llega la respuesta HTTP; no es una prueba de procesamiento remoto byte a byte.
- Un proxy que rechace chunked puede hacer fallar pruebas con un servidor que sí funcionaría usando `Content-Length`.
- El tráfico de validación precede a las métricas y puede afectar mínimamente una conexión muy lenta.
- La falta de gracia de TCP (a diferencia del worker oficial) puede incluir ramp-up y subestimar enlaces rápidos o cortos.
- Un servidor, CDN, TLS, congestión, un único flujo y la duración elegida condicionan el resultado; no demuestran por sí solos la capacidad contractual del ISP.

## 7. Pruebas que faltan

1. Servidor HTTP local controlado que entregue 2xx, 4xx/5xx, respuesta truncada, cuerpo lento y respuesta nunca completada.
2. Cancelación real durante: conexión/validación, ping bloqueado, `InputStream.read` bloqueado y `OutputStream.write` bloqueado; comprobar cierre y resultado `CANCELLED` sin `COMPLETED` posterior.
3. Upload contra servidor que registra `Transfer-Encoding`, bytes recibidos y 2xx/413/5xx; comprobar que un rechazo no deja Mbps de subida válido.
4. Verificación de liberación de sockets/streams mediante servidor de prueba que detecte cierre tras cada fase.
5. TLS válido, certificado inválido, redirección 3xx (debe fallar de forma explícita) y timeout de conexión/lectura.
6. Integración con una instancia LibreSpeed propia, manteniendo las pruebas JVM sin Internet.

## 8. Prueba de integración reproducible propuesta

No hay servidor configurado en el proyecto, por lo que esta auditoría no fija uno público. Para validar realmente:

1. Levantar una instancia propia de LibreSpeed desde el repositorio oficial, siguiendo su guía de instalación, en un entorno de pruebas controlado con HTTPS válido si se prueba HTTPS.
2. Conservar los backends oficiales `backend/empty.php` y `backend/garbage.php`; configurar el servidor web para permitir cuerpos POST grandes, como exige LibreSpeed.
3. Construir un `SpeedTestServer` de prueba con URLs absolutas del entorno propio: `pingUrl` y `uploadUrl` a `empty.php`; `downloadUrl` a `garbage.php`.
4. Ejecutar manualmente cinco casos: éxito, 413/rechazo de upload, descarga truncada, timeout y cancelación en cada fase. Registrar status, bytes, errores y que no haya `COMPLETED` tras cancelar.
5. Ejecutar las pruebas JVM sin red como puerta de regresión independiente.

## 9. Comando de pruebas ejecutado

```bash
bash ./gradlew :app:testDebugUnitTest
```

Resultado: **BUILD SUCCESSFUL** (pruebas JVM de la aplicación, incluidas las seis pruebas actuales del paquete `speedtest`).
