package dev.khoj.pitaka.data.publish

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM tests for [PublishCoverIds]. The salt store is faked so the
 * tests touch neither Robolectric nor EncryptedSharedPreferences — the
 * production storage seam is exercised at integration time.
 */
class PublishCoverIdsTest {

    private class FakeSaltStore(private val salt: ByteArray) : PublishCoverSaltStore {
        var callCount: Int = 0
            private set
        override fun salt(): ByteArray {
            callCount++
            return salt
        }
    }

    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun pathFor_returns_covers_prefix_jpg_suffix() {
        val ids = PublishCoverIds(FakeSaltStore(salt))
        val path = ids.pathFor(42L)
        assertThat(path).startsWith("covers/")
        assertThat(path).endsWith(".jpg")
    }

    @Test
    fun pathFor_hash_length_matches_constant() {
        val ids = PublishCoverIds(FakeSaltStore(salt))
        val path = ids.pathFor(42L)
        // covers/<HASH>.jpg
        val hash = path.removePrefix("covers/").removeSuffix(".jpg")
        assertThat(hash).hasLength(PublishCoverIds.HASH_HEX_CHARS)
        assertThat(hash).matches("^[0-9a-f]+$")
    }

    @Test
    fun pathFor_is_deterministic_for_same_id() {
        val ids = PublishCoverIds(FakeSaltStore(salt))
        val a = ids.pathFor(42L)
        val b = ids.pathFor(42L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun pathFor_differs_for_different_ids() {
        val ids = PublishCoverIds(FakeSaltStore(salt))
        val seen = (1L..50L).map { ids.pathFor(it) }.toSet()
        // 50 distinct ids → 50 distinct paths (collision-free in this range
        // at 64 bits of hash).
        assertThat(seen).hasSize(50)
    }

    @Test
    fun pathFor_differs_when_salt_differs() {
        val a = PublishCoverIds(FakeSaltStore(ByteArray(16) { 0 }))
        val b = PublishCoverIds(FakeSaltStore(ByteArray(16) { 1 }))
        assertThat(a.pathFor(42L)).isNotEqualTo(b.pathFor(42L))
    }

    @Test
    fun shortHashHex_is_a_pure_function() {
        // Same inputs → same output, without going through any store.
        val a = PublishCoverIds.shortHashHex(salt, 12345L)
        val b = PublishCoverIds.shortHashHex(salt, 12345L)
        assertThat(a).isEqualTo(b)
        assertThat(a).hasLength(PublishCoverIds.HASH_HEX_CHARS)
    }

    @Test
    fun shortHashHex_changes_with_id() {
        val a = PublishCoverIds.shortHashHex(salt, 1L)
        val b = PublishCoverIds.shortHashHex(salt, 2L)
        assertThat(a).isNotEqualTo(b)
    }
}
