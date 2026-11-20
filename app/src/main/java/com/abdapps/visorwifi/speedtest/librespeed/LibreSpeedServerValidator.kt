package com.abdapps.visorwifi.speedtest.librespeed

import com.abdapps.visorwifi.speedtest.domain.SpeedTestProtocol
import com.abdapps.visorwifi.speedtest.domain.SpeedTestServer
import java.net.URI

/**
 * Validación ligera: sintaxis, protocolo y un GET al ping endpoint. No transfiere basura ni
 * declara compatibles los endpoints de descarga/subida hasta que la prueba completa los verifica.
 */
open class LibreSpeedServerValidator {
    open suspend fun requireAccessible(server: SpeedTestServer, client: LibreSpeedClient, timeoutMillis: Int): SpeedTestServer {
        val urls = listOf(server.baseUrl, server.pingUrl, server.downloadUrl, server.uploadUrl)
        urls.forEach { url ->
            val uri = try { URI(url) } catch (e: Exception) { throw IllegalArgumentException("Invalid server URL: $url", e) }
            require(uri.scheme == "http" || uri.scheme == "https") { "Unsupported URL scheme: $url" }
            require(!uri.host.isNullOrBlank()) { "URL requires a host: $url" }
        }
        val expected = if (server.protocol == SpeedTestProtocol.HTTPS) "https" else "http"
        require(URI(server.baseUrl).scheme == expected) { "Server protocol does not match baseUrl" }
        // LibreSpeed empty.php normally returns un 2xx vacío: sólo acredita accesibilidad del ping.
        client.ping(server, 1, timeoutMillis, System::nanoTime).also {
            if (it.successful == 0) throw IllegalStateException("LibreSpeed ping endpoint is unavailable")
        }
        return server.copy(validationStatus = com.abdapps.visorwifi.speedtest.domain.ServerValidationStatus.ACCESSIBLE)
    }
}
