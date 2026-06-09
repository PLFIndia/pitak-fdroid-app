package dev.khoj.pitaka.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.khoj.pitaka.data.local.books.BookDao
import dev.khoj.pitaka.data.local.books.BooksDatabase
import dev.khoj.pitaka.data.local.cache.CacheDatabase
import dev.khoj.pitaka.data.local.cache.IsbnMetadataCacheDao
import dev.khoj.pitaka.data.local.wishlist.WishlistBookDao
import dev.khoj.pitaka.data.local.wishlist.WishlistDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBooksDatabase(@ApplicationContext context: Context): BooksDatabase =
        Room.databaseBuilder(
            context,
            BooksDatabase::class.java,
            BooksDatabase.NAME,
        )
            .addMigrations(BooksDatabase.MIGRATION_1_2, BooksDatabase.MIGRATION_2_3, BooksDatabase.MIGRATION_3_4, BooksDatabase.MIGRATION_4_5, BooksDatabase.MIGRATION_5_6, BooksDatabase.MIGRATION_6_7, BooksDatabase.MIGRATION_7_8, BooksDatabase.MIGRATION_8_9, BooksDatabase.MIGRATION_9_10)
            // Room 2.6.1: no destructive-downgrade flag is set (downgrade throws).
            // When Room 2.7+ becomes the project baseline we can switch to
            // .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false).
            .build()

    @Provides
    fun provideBookDao(database: BooksDatabase): BookDao = database.bookDao()

    @Provides
    @Singleton
    fun provideCacheDatabase(@ApplicationContext context: Context): CacheDatabase =
        Room.databaseBuilder(
            context,
            CacheDatabase::class.java,
            CacheDatabase.NAME,
        ).build()

    @Provides
    fun provideIsbnMetadataCacheDao(database: CacheDatabase): IsbnMetadataCacheDao =
        database.isbnMetadataCacheDao()

    @Provides
    @Singleton
    fun provideWishlistDatabase(@ApplicationContext context: Context): WishlistDatabase =
        Room.databaseBuilder(
            context,
            WishlistDatabase::class.java,
            WishlistDatabase.NAME,
        ).build()

    @Provides
    fun provideWishlistBookDao(database: WishlistDatabase): WishlistBookDao =
        database.wishlistBookDao()
}
