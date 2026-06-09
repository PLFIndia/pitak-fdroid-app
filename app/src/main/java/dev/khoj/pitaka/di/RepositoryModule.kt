package dev.khoj.pitaka.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.khoj.pitaka.data.images.CoverHealFlag
import dev.khoj.pitaka.data.images.PrefsCoverHealFlag
import dev.khoj.pitaka.data.repository.BookRepositoryImpl
import dev.khoj.pitaka.data.repository.BorrowerRepositoryImpl
import dev.khoj.pitaka.data.repository.LoanRepositoryImpl
import dev.khoj.pitaka.data.repository.WishlistRepositoryImpl
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import dev.khoj.pitaka.domain.repository.WishlistRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindWishlistRepository(impl: WishlistRepositoryImpl): WishlistRepository

    @Binds
    @Singleton
    abstract fun bindBorrowerRepository(impl: BorrowerRepositoryImpl): BorrowerRepository

    @Binds
    @Singleton
    abstract fun bindLoanRepository(impl: LoanRepositoryImpl): LoanRepository

    @Binds
    @Singleton
    abstract fun bindCoverHealFlag(impl: PrefsCoverHealFlag): CoverHealFlag
}
