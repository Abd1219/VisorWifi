package com.abdapps.visorwifi.speedtest

import com.abdapps.visorwifi.speedtest.domain.SpeedTestMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTestMetricsTest {
    @Test fun calculatesDecimalMbpsFromActualBytesAndMonotonicDuration() {
        assertEquals(8.0, SpeedTestMetrics.megabitsPerSecond(1_000_000, 1_000_000_000)!!, 0.0001)
    }

    @Test fun rejectsInvalidDurationInsteadOfInventingSpeed() {
        assertNull(SpeedTestMetrics.megabitsPerSecond(10, 0))
        assertNull(SpeedTestMetrics.megabitsPerSecond(-1, 1))
    }

    @Test fun jitterIsMeanAbsoluteSuccessiveDifference() {
        assertEquals(12.5, SpeedTestMetrics.jitterMillis(listOf(10.0, 30.0, 25.0))!!, 0.0001)
        assertNull(SpeedTestMetrics.jitterMillis(listOf(10.0)))
    }
}
