package dev.khoj.pitaka.data.prefs

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plain Application so Robolectric does not instantiate the real
 * [dev.khoj.pitaka.PitakaApplication], whose onCreate opens the
 * `pitaka_app_prefs` DataStore (via the cover-heal flag). That second
 * DataStore instance on the same file collides with the one this test
 * constructs directly — DataStore forbids two instances per file.
 */
class AppPrefsStubApp : android.app.Application()

/**
 * F-04: the two GitHub pre-submit disclosure acknowledgement flags persist
 * independently and default to false. These flags gate the disclosure dialog
 * (once-then-remembered) for the translation and crash submission flows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AppPrefsStubApp::class)
class AppPreferencesDisclosureTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() = runBlocking {
        prefs = AppPreferences(ApplicationProvider.getApplicationContext())
        // DataStore file is shared across tests in the same Robolectric
        // process; reset both flags so each test starts from a known state.
        prefs.setTranslateDisclosureAck(false)
        prefs.setCrashDisclosureAck(false)
    }

    @Test
    fun translate_ack_defaults_false() = runBlocking {
        assertThat(prefs.translateDisclosureAck().first()).isFalse()
    }

    @Test
    fun crash_ack_defaults_false() = runBlocking {
        assertThat(prefs.crashDisclosureAck().first()).isFalse()
    }

    @Test
    fun translate_ack_round_trips_true() = runBlocking {
        prefs.setTranslateDisclosureAck(true)
        assertThat(prefs.translateDisclosureAck().first()).isTrue()
    }

    @Test
    fun crash_ack_round_trips_true() = runBlocking {
        prefs.setCrashDisclosureAck(true)
        assertThat(prefs.crashDisclosureAck().first()).isTrue()
    }

    @Test
    fun acking_translate_does_not_ack_crash() = runBlocking {
        // The whole point of two separate flags: consenting to publish a
        // translation must not silently consent to publishing the larger
        // crash payload (device + locale + stack frames).
        prefs.setTranslateDisclosureAck(true)
        assertThat(prefs.translateDisclosureAck().first()).isTrue()
        assertThat(prefs.crashDisclosureAck().first()).isFalse()
    }

    @Test
    fun acking_crash_does_not_ack_translate() = runBlocking {
        prefs.setCrashDisclosureAck(true)
        assertThat(prefs.crashDisclosureAck().first()).isTrue()
        assertThat(prefs.translateDisclosureAck().first()).isFalse()
    }

    @Test
    fun ack_can_be_reset_to_false() = runBlocking {
        prefs.setTranslateDisclosureAck(true)
        prefs.setTranslateDisclosureAck(false)
        assertThat(prefs.translateDisclosureAck().first()).isFalse()
    }
}
