package dev.khoj.pitaka.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.khoj.pitaka.data.security.AppLockStore
import dev.khoj.pitaka.data.security.Argon2PinHasher
import dev.khoj.pitaka.data.security.EncryptedAppLockStore
import dev.khoj.pitaka.data.security.PinHasher
import javax.inject.Singleton

/**
 * Binds the App Lock storage + hashing seams to their production implementations
 * (EncryptedSharedPreferences + Argon2id). Tests inject in-memory fakes instead.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindAppLockStore(impl: EncryptedAppLockStore): AppLockStore

    @Binds
    @Singleton
    abstract fun bindPinHasher(impl: Argon2PinHasher): PinHasher
}
