package dev.khoj.pitaka.data.crypto.aead

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Wave 6.1 (audit F-03 spine): [AeadPreferenceStore] over a real Robolectric
 * [android.content.SharedPreferences], with a plain JCE AES key standing in
 * for the on-device Keystore key (see AesGcmAeadTest for why).
 *
 * Asserts the behaviours the migrated stores will depend on: round-trip,
 * absence, corrupt-degrades-to-null (NOT crash), key-name binding, and that
 * the on-disk value is not the plaintext.
 */
@RunWith(RobolectricTestRunner::class)
class AeadPreferenceStoreTest {

    private lateinit var key: SecretKey
    private lateinit var store: AeadPreferenceStore
    private lateinit var rawPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        rawPrefs = ctx.getSharedPreferences("aead_store_test", Context.MODE_PRIVATE)
        rawPrefs.edit().clear().apply()
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        store = AeadPreferenceStore(rawPrefs, AesGcmAead(key))
    }

    @Test
    fun put_then_get_round_trips() {
        store.putString("gh_token", "ghp_secret_123")
        assertThat(store.getString("gh_token")).isEqualTo("ghp_secret_123")
    }

    @Test
    fun missing_key_returns_null() {
        assertThat(store.getString("absent")).isNull()
        assertThat(store.contains("absent")).isFalse()
    }

    @Test
    fun stored_value_is_not_plaintext_on_disk() {
        store.putString("pin_hash", "argon2-hash-value")
        val onDisk = rawPrefs.getString("pin_hash", null)
        assertThat(onDisk).isNotNull()
        assertThat(onDisk).doesNotContain("argon2-hash-value")
    }

    @Test
    fun corrupt_value_degrades_to_null_not_crash() {
        rawPrefs.edit().putString("gh_token", "!!!not-base64-or-ciphertext!!!").apply()
        assertThat(store.getString("gh_token")).isNull()
    }

    @Test
    fun ciphertext_copied_to_a_different_key_name_does_not_decrypt() {
        // AAD = key name, so a value moved between slots fails (anti-shuffle).
        store.putString("slotA", "value")
        val blob = rawPrefs.getString("slotA", null)!!
        rawPrefs.edit().putString("slotB", blob).apply()
        assertThat(store.getString("slotA")).isEqualTo("value")
        assertThat(store.getString("slotB")).isNull()
    }

    @Test
    fun remove_deletes_the_entry() {
        store.putString("k", "v")
        store.remove("k")
        assertThat(store.getString("k")).isNull()
    }

    @Test
    fun clear_empties_the_store() {
        store.putString("a", "1")
        store.putString("b", "2")
        store.clear()
        assertThat(store.getString("a")).isNull()
        assertThat(store.getString("b")).isNull()
    }

    @Test
    fun bytes_round_trip_through_their_own_api() {
        val raw = ByteArray(64) { it.toByte() }
        store.putBytes("blob", raw)
        assertThat(store.getBytes("blob")).isEqualTo(raw)
    }
}
