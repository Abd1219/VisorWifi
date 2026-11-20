package com.abdapps.visorwifi.speedtest.domain

enum class SpeedTestServerType { PUBLIC, PRIVATE }
enum class SpeedTestProtocol { HTTP, HTTPS }
/**
 * ACCESSIBLE sólo acredita una sonda HTTP ligera de ping. COMPATIBLE exige que la
 * prueba completa haya terminado. No se infieren capacidades de upload/download desde ping.
 */
enum class ServerValidationStatus { NOT_VALIDATED, ACCESSIBLE, COMPATIBLE, INCOMPATIBLE, VALIDATION_ERROR }

/** Endpoints declarados por un backend compatible con LibreSpeed. */
data class SpeedTestServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val pingUrl: String,
    val downloadUrl: String,
    val uploadUrl: String,
    val serverType: SpeedTestServerType,
    val protocol: SpeedTestProtocol,
    val location: String? = null,
    val validationStatus: ServerValidationStatus = ServerValidationStatus.NOT_VALIDATED
)
