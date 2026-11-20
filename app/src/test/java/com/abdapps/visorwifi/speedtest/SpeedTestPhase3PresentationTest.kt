package com.abdapps.visorwifi.speedtest

import com.abdapps.visorwifi.AppScreen
import com.abdapps.visorwifi.speedtest.config.NoApprovedSpeedTestServerSource
import com.abdapps.visorwifi.speedtest.domain.SpeedTestStatus
import com.abdapps.visorwifi.viewmodel.SpeedTestPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure UI-contract tests; none starts HTTP or claims a synthetic result is a real measurement. */
class SpeedTestPhase3PresentationTest {
    @Test fun bottomNavigationExposesFourStableDestinations() {
        assertEquals(
            listOf(AppScreen.MONITOR, AppScreen.AP_LOCATOR, AppScreen.SPEED_TEST, AppScreen.REPORTS),
            AppScreen.entries.toList()
        )
    }

    @Test fun noApprovedSourceLeavesRealMeasurementUnavailable() {
        assertEquals(null, NoApprovedSpeedTestServerSource.approvedServer())
    }

    @Test fun activeEnginePhasesAreTheOnlyCancellableStates() {
        assertTrue(SpeedTestPresentation.isRunning(SpeedTestStatus.PREPARING))
        assertTrue(SpeedTestPresentation.isRunning(SpeedTestStatus.PINGING))
        assertTrue(SpeedTestPresentation.isRunning(SpeedTestStatus.DOWNLOADING))
        assertTrue(SpeedTestPresentation.isRunning(SpeedTestStatus.UPLOADING))
        assertFalse(SpeedTestPresentation.isRunning(SpeedTestStatus.IDLE))
        assertFalse(SpeedTestPresentation.isRunning(SpeedTestStatus.COMPLETED))
    }

    @Test fun terminalStatesHaveHonestUserLabels() {
        assertEquals("Prueba completada.", SpeedTestPresentation.phaseLabel(SpeedTestStatus.COMPLETED))
        assertEquals("No se pudo completar la prueba.", SpeedTestPresentation.phaseLabel(SpeedTestStatus.FAILED))
        assertEquals("Prueba cancelada.", SpeedTestPresentation.phaseLabel(SpeedTestStatus.CANCELLED))
    }
}
