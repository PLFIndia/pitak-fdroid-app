package dev.khoj.pitaka.data.vault

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.data.local.borrowers.BorrowersDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F-12: the FLAG_SECURE decision is a pure function of vault state. We test
 * the decision here; the window-flag application in MainActivity is an
 * Android boundary covered by manual / instrumentation verification.
 */
@RunWith(RobolectricTestRunner::class)
class VaultWindowSecurityTest {

    @Test
    fun locked_state_does_not_require_secure() {
        assertThat(VaultWindowSecurity.shouldSecure(VaultState.Locked)).isFalse()
    }

    @Test
    fun unlocked_state_requires_secure() {
        // An in-memory BorrowersDatabase stands in for the unlocked vault DB.
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BorrowersDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            assertThat(VaultWindowSecurity.shouldSecure(VaultState.Unlocked(db))).isTrue()
        } finally {
            db.close()
        }
    }
}
