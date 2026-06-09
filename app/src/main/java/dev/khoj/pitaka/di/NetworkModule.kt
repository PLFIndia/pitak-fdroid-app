package dev.khoj.pitaka.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.khoj.pitaka.BuildConfig
import dev.khoj.pitaka.data.remote.ChainedIsbnLookup
import dev.khoj.pitaka.data.remote.googlebooks.GoogleBooksApi
import dev.khoj.pitaka.data.remote.googlebooks.GoogleBooksLookupService
import dev.khoj.pitaka.data.remote.openlibrary.OpenLibraryApi
import dev.khoj.pitaka.data.remote.openlibrary.OpenLibraryLookupService
import dev.khoj.pitaka.domain.lookup.IsbnLookupService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val OPEN_LIBRARY_BASE = "https://openlibrary.org/"
    private const val GOOGLE_BOOKS_BASE = "https://www.googleapis.com/"
    private const val GITHUB_AUTH_BASE = "https://github.com/"
    private const val GITHUB_API_BASE = "https://api.github.com/"

    /** Hilt qualifier for the cert-pinned GitHub-only OkHttp client (F-08). */
    const val GITHUB_CLIENT = "github"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi =
        Moshi.Builder()
            // Custom AgeGroup adapter MUST precede the reflective factory so it
            // wins for Book.AgeGroup: it writes the stable token and tolerantly
            // reads legacy enum names from older backups (see AgeGroupJsonAdapter).
            .add(dev.khoj.pitaka.data.export.AgeGroupJsonAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logger)
        }
        return builder.build()
    }

    /**
     * F-08: GitHub-only OkHttp client with certificate pinning. Scoped to the
     * publish + OAuth APIs (`github.com`, `api.github.com`) so the pins never
     * apply to OpenLibrary / Google Books / cover fetches — those stay on the
     * default [provideOkHttp] client. Built by copying the default client's
     * tuned config (timeouts, retry, debug logging) and adding the pinner, so
     * the two clients stay behaviourally identical apart from pinning.
     */
    @Provides
    @Singleton
    @Named(GITHUB_CLIENT)
    fun provideGitHubOkHttp(default: OkHttpClient): OkHttpClient =
        default.newBuilder()
            .certificatePinner(GitHubCertificatePins.pinner())
            .build()

    @Provides
    @Singleton
    fun provideOpenLibraryApi(client: OkHttpClient, moshi: Moshi): OpenLibraryApi =
        Retrofit.Builder()
            .baseUrl(OPEN_LIBRARY_BASE)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenLibraryApi::class.java)

    @Provides
    @Singleton
    fun provideGoogleBooksApi(client: OkHttpClient, moshi: Moshi): GoogleBooksApi =
        Retrofit.Builder()
            .baseUrl(GOOGLE_BOOKS_BASE)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleBooksApi::class.java)

    @Provides
    @Singleton
    fun provideGitHubAuthApi(@Named(GITHUB_CLIENT) client: OkHttpClient, moshi: Moshi): dev.khoj.pitaka.data.publish.github.GitHubAuthApi =
        Retrofit.Builder()
            .baseUrl(GITHUB_AUTH_BASE)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(dev.khoj.pitaka.data.publish.github.GitHubAuthApi::class.java)

    @Provides
    @Singleton
    fun provideGitHubContentsApi(@Named(GITHUB_CLIENT) client: OkHttpClient, moshi: Moshi): dev.khoj.pitaka.data.publish.github.GitHubContentsApi =
        Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(dev.khoj.pitaka.data.publish.github.GitHubContentsApi::class.java)

    @Provides
    @Singleton
    fun provideGitHubGitDataApi(@Named(GITHUB_CLIENT) client: OkHttpClient, moshi: Moshi): dev.khoj.pitaka.data.publish.github.GitHubGitDataApi =
        Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(dev.khoj.pitaka.data.publish.github.GitHubGitDataApi::class.java)

    @Provides
    @Singleton
    @Named(ChainedIsbnLookup.PRIMARY)
    fun providePrimaryLookup(impl: OpenLibraryLookupService): IsbnLookupService = impl

    @Provides
    @Singleton
    @Named(ChainedIsbnLookup.FALLBACK)
    fun provideFallbackLookup(impl: GoogleBooksLookupService): IsbnLookupService = impl

    @Provides
    @Singleton
    fun provideIsbnLookupService(chained: ChainedIsbnLookup): IsbnLookupService = chained
}
