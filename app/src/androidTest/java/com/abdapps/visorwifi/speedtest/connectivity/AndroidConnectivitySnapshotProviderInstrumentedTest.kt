package com.abdapps.visorwifi.speedtest.connectivity

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test on a real Android framework instance. It intentionally does not claim to simulate
 * transport changes or permission revocation, which require device/emulator control.
 */
@RunWith(AndroidJUnit4::class)
class AndroidConnectivitySnapshotProviderInstrumentedTest {
    @Test fun readsCurrentSnapshotAndReleasesSingleObservation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AndroidConnectivitySnapshotProvider(context)
        val current = provider.currentSnapshot()
        val observed = provider.observe().first()
        assertTrue(current.timestampMillis > 0)
        assertTrue(observed.timestampMillis > 0)
    }
}
