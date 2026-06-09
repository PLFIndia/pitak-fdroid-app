package dev.khoj.pitaka.data.local.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class IsbnMetadataCacheDaoTest {

    private lateinit var db: CacheDatabase
    private lateinit var dao: IsbnMetadataCacheDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.isbnMetadataCacheDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun upsert_then_get_returns_payload() = runBlocking {
        dao.upsert(IsbnMetadataCacheEntity("978", "{}", "primary", 100L))
        val fetched = dao.get("978")
        assertThat(fetched).isNotNull()
        assertThat(fetched!!.payload).isEqualTo("{}")
        assertThat(fetched.source).isEqualTo("primary")
        assertThat(fetched.fetchedAt).isEqualTo(100L)
    }

    @Test
    fun upsert_replaces_existing() = runBlocking {
        dao.upsert(IsbnMetadataCacheEntity("978", "v1", "primary", 100L))
        dao.upsert(IsbnMetadataCacheEntity("978", "v2", "fallback", 200L))
        val fetched = dao.get("978")
        assertThat(fetched!!.payload).isEqualTo("v2")
        assertThat(fetched.source).isEqualTo("fallback")
    }

    @Test
    fun delete_removes_row() = runBlocking {
        dao.upsert(IsbnMetadataCacheEntity("978", "{}", "primary", 100L))
        dao.delete("978")
        assertThat(dao.get("978")).isNull()
    }

    @Test
    fun deleteOlderThan_removes_only_stale_rows() = runBlocking {
        dao.upsert(IsbnMetadataCacheEntity("a", "{}", "p", 100L))
        dao.upsert(IsbnMetadataCacheEntity("b", "{}", "p", 500L))
        dao.upsert(IsbnMetadataCacheEntity("c", "{}", "p", 1_000L))
        val removed = dao.deleteOlderThan(600L)
        assertThat(removed).isEqualTo(2)
        assertThat(dao.get("a")).isNull()
        assertThat(dao.get("b")).isNull()
        assertThat(dao.get("c")).isNotNull()
    }
}
