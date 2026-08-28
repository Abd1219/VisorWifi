package com.abdapps.visorwifi

import com.abdapps.visorwifi.model.PdfGenerator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias para validar la sanitización de caracteres y compatibilidad PDF
 * en [PdfGenerator.sanitizePdfText].
 */
class PdfSanitizerTest {

    @Test
    fun testWordIntegrity_oficinasAndConfianza() {
        assertEquals("Oficinas", PdfGenerator.sanitizePdfText("Oficinas"))
        assertEquals("Confianza: 95%", PdfGenerator.sanitizePdfText("Confianza: 95%"))
        // Si el texto trae ligaduras Unicode explícitas, deben normalizarse
        assertEquals("Oficinas", PdfGenerator.sanitizePdfText("O\uFB01cinas"))
        assertEquals("Confianza", PdfGenerator.sanitizePdfText("Con\uFB01anza"))
    }

    @Test
    fun testTimeRange_enDashReplacedWithHyphen() {
        val input = "11:50:57 – 11:56:11"
        val expected = "11:50:57 - 11:56:11"
        assertEquals(expected, PdfGenerator.sanitizePdfText(input))
    }

    @Test
    fun testSpanishAccentsAndTildesPreserved() {
        val input = "Técnico: Juan Pérez, Ubicación: Planta Baja, Señal: Óptima"
        assertEquals(input, PdfGenerator.sanitizePdfText(input))
    }

    @Test
    fun testMathematicalAndComparisonSymbols() {
        assertEquals(">= 5 eventos", PdfGenerator.sanitizePdfText("≥ 5 eventos"))
        assertEquals("<= 2%", PdfGenerator.sanitizePdfText("≤ 2%"))
        assertEquals("~ 5m 14s", PdfGenerator.sanitizePdfText("≈ 5m 14s"))
        assertEquals("7.2 dBm^2", PdfGenerator.sanitizePdfText("7.2 dBm²"))
        assertEquals("[OK]", PdfGenerator.sanitizePdfText("✓"))
    }

    @Test
    fun testEmojiSanitizationForPdf() {
        val warningInput = "⚠️ Advertencia de red"
        assertEquals("[!] Advertencia de red", PdfGenerator.sanitizePdfText(warningInput))

        val roamingInput = "[11:55:00] 📡 Roaming: AP_1 ➔ AP_2 (RSSI: -68 dBm)"
        assertEquals("[11:55:00] [AP] Roaming: AP_1 -> AP_2 (RSSI: -68 dBm)", PdfGenerator.sanitizePdfText(roamingInput))

        val ssidChangeInput = "[11:52:00] 🔀 Cambio de RED: \"Office_5G\" ➔ \"Office_2G\""
        assertEquals("[11:52:00] [roaming] Cambio de RED: \"Office_5G\" -> \"Office_2G\"", PdfGenerator.sanitizePdfText(ssidChangeInput))
    }

    @Test
    fun testUnitsAndPercentages() {
        val input = "Latencia: 7.81 ms, WAN: 19.82 ms, Pérdida: 0.0%, RSSI: -64.1 dBm, PHY: 540 Mbps"
        assertEquals(input, PdfGenerator.sanitizePdfText(input))
    }
}
