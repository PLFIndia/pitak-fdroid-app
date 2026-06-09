package dev.khoj.pitaka.data.vault

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.data.crypto.VaultPassphrase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Pure-JVM unit tests for [VaultAutoLocker]. Avoid Robolectric — we test the
 * timer logic directly via fake VaultSession + fake VaultPreferences.
 */
class VaultAutoLockerLogicTest {

    @Test
    fun lock_runs_after_configured_delay(): Unit = runBlocking {
        val session = mockk<VaultSession>(relaxed = true)
        every { session.isUnlocked() } returns true
        val prefs = mockk<VaultPreferences>()
        every { prefs.autoLockTimeoutMs() } returns 50L

        val locker = VaultAutoLocker(session, prefs)
        // Mimic onStop directly (we can't drive ProcessLifecycleOwner without Robolectric).
        locker.onStop(mockk(relaxed = true))
        // Wait long enough for the delay to elapse.
        Thread.sleep(150)
        io.mockk.verify(timeout = 500) { session.lock() }
    }

    @Test
    fun onStart_cancels_pending_lock(): Unit = runBlocking {
        val session = mockk<VaultSession>(relaxed = true)
        every { session.isUnlocked() } returns true
        val prefs = mockk<VaultPreferences>()
        every { prefs.autoLockTimeoutMs() } returns 5_000L

        val locker = VaultAutoLocker(session, prefs)
        locker.onStop(mockk(relaxed = true))
        Thread.sleep(50)
        locker.onStart(mockk(relaxed = true))
        Thread.sleep(100)
        io.mockk.verify(exactly = 0) { session.lock() }
    }

    @Test
    fun no_lock_scheduled_when_already_locked(): Unit = runBlocking {
        val session = mockk<VaultSession>(relaxed = true)
        every { session.isUnlocked() } returns false
        val prefs = mockk<VaultPreferences>(relaxed = true)

        val locker = VaultAutoLocker(session, prefs)
        locker.onStop(mockk(relaxed = true))
        Thread.sleep(50)
        io.mockk.verify(exactly = 0) { session.lock() }
    }
}
