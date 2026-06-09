package dev.khoj.pitaka.data.local.wishlist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class WishlistBookDaoTest {

    private lateinit var db: WishlistDatabase
    private lateinit var dao: WishlistBookDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WishlistDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.wishlistBookDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun activeByPriority_sorts_High_first_then_date_desc(): Unit = runBlocking {
        dao.upsert(entity(title = "A-low",  priority = 0, addedDate = 1_000))
        dao.upsert(entity(title = "B-med",  priority = 1, addedDate = 2_000))
        dao.upsert(entity(title = "C-high", priority = 2, addedDate = 1_500))
        dao.upsert(entity(title = "D-high-newer", priority = 2, addedDate = 3_000))

        val list = dao.observeActiveByPriority().first()
        assertThat(list.map { it.title }).containsExactly(
            "D-high-newer", "C-high", "B-med", "A-low",
        ).inOrder()
    }

    @Test
    fun purchased_rows_excluded_from_active(): Unit = runBlocking {
        dao.upsert(entity(title = "active", priority = 1, addedDate = 1))
        dao.upsert(entity(title = "bought", priority = 1, addedDate = 2, purchased = true,
            purchasedDate = 100L))

        val active = dao.observeActiveByPriority().first().map { it.title }
        val purchased = dao.observePurchased().first().map { it.title }
        assertThat(active).containsExactly("active")
        assertThat(purchased).containsExactly("bought")
    }

    @Test
    fun search_matches_title_transliteration_author_isbn(): Unit = runBlocking {
        dao.upsert(entity(title = "गोदान", titleTranslit = "Godaan", isbn = "9780000000001"))
        dao.upsert(entity(title = "Sapiens", author = "Yuval Harari"))
        dao.upsert(entity(title = "Tractatus"))

        assertThat(dao.search("godaan").first().map { it.title }).containsExactly("गोदान")
        assertThat(dao.search("Harari").first().map { it.title }).containsExactly("Sapiens")
        assertThat(dao.search("Tract").first().map { it.title }).containsExactly("Tractatus")
        assertThat(dao.search("000001").first().map { it.title }).containsExactly("गोदान")
    }

    @Test
    fun findByIsbn_returns_match_or_null(): Unit = runBlocking {
        dao.upsert(entity(title = "x", isbn = "9780140428445"))
        assertThat(dao.findByIsbn("9780140428445")?.title).isEqualTo("x")
        assertThat(dao.findByIsbn("0000000000000")).isNull()
    }

    @Test
    fun deleteById_removes_row(): Unit = runBlocking {
        val id = dao.upsert(entity(title = "throwaway"))
        assertThat(dao.count()).isEqualTo(1)
        dao.deleteById(id)
        assertThat(dao.count()).isEqualTo(0)
    }

    private fun entity(
        id: Long = 0L,
        title: String,
        titleTranslit: String? = null,
        author: String? = null,
        isbn: String? = null,
        priority: Int = 1,
        addedDate: Long = System.currentTimeMillis(),
        purchased: Boolean = false,
        purchasedDate: Long? = null,
    ) = WishlistBookEntity(
        id = id,
        title = title,
        titleTransliteration = titleTranslit,
        author = author,
        isbn = isbn,
        publisher = null,
        publishedYear = null,
        coverUrl = null,
        priceEstimate = null,
        priority = priority,
        notes = null,
        source = "MANUAL",
        addedDate = addedDate,
        purchased = purchased,
        purchasedDate = purchasedDate,
        needsMetadata = false,
    )
}
