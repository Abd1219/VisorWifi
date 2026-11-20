package com.abdapps.visorwifi.speedtest.connectivity

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Minimal registration seam so callback lifetime can be verified on the JVM. */
internal interface SnapshotCallbackRegistration {
    fun register(onChanged: () -> Unit)
    fun unregister()
}

internal class SnapshotObservation(
    private val snapshot: () -> NetworkSnapshot,
    private val registrationFactory: () -> SnapshotCallbackRegistration
) {
    fun observe(): Flow<NetworkSnapshot> = callbackFlow {
        val registration = registrationFactory()
        try {
            registration.register { trySend(snapshot()) }
            trySend(snapshot())
        } catch (_: SecurityException) {
            trySend(NetworkSnapshot.noNetwork(System.currentTimeMillis()))
            close()
            return@callbackFlow
        }
        awaitClose { registration.unregister() }
    }.distinctUntilChanged { old, new -> old.copy(timestampMillis = 0) == new.copy(timestampMillis = 0) }
}
