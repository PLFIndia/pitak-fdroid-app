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

/** Plain Application so Robolectric doesn't open the real PitakaApplication's
 *  DataStore on the same file (DataStore forbids two instances per file). */
class LibIdStubApp : android.app.Application()

/**
 * The persistence boundary [AppPreferences.setLibraryId] enforces the library-ID
 * string limitation for EVERY caller (QR adopt, file merge, regenerate), so no
 * path can write a malformed ID that would propagate into future exports and
 * break QR pairing. Three-way contract: blank clears, valid sets, junk is a no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = LibIdStubApp::class)
class AppPreferencesLibraryIdTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() = runBlocking {
        prefs = AppPreferences(ApplicationProvider.getApplicationContext())
        prefs.setLibraryId("") // reset (shared DataStore file across tests)
    }

    @Test
    fun valid_id_is_persisted() = runBlocking {
        val id = "a".repeat(32)
        prefs.setLibraryId(id)
        assertThat(prefs.libraryId().first()).isEqualTo(id)
    }

    @Test
    fun junk_id_is_rejected_leaving_current_id_intact() = runBlocking {
        val good = "0123456789abcdef0123456789abcdef"
        prefs.setLibraryId(good)
        // Each of these violates the rule and must be a no-op.
        prefs.setLibraryId("LIB-A")               // uppercase + hyphen + too short
        prefs.setLibraryId("Z".repeat(9000))      // oversized + non-hex
        prefs.setLibraryId("'; DROP TABLE x;--")  // injection junk
        prefs.setLibraryId("ABCDEF0123456789")    // uppercase hex
        assertThat(prefs.libraryId().first()).isEqualTo(good)
    }

    @Test
    fun blank_clears_the_id() = runBlocking {
        prefs.setLibraryId("a".repeat(32))
        prefs.setLibraryId("")
        assertThat(prefs.libraryId().first()).isEmpty()
    }

    @Test
    fun getOrCreate_mints_a_valid_id() = runBlocking {
        prefs.setLibraryId("")
        val minted = prefs.getOrCreateLibraryId()
        assertThat(dev.khoj.pitaka.domain.model.LibraryId.isValid(minted)).isTrue()
    }
}
