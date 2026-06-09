package dev.khoj.pitaka.data.publish.github

import com.squareup.moshi.Json
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * GitHub Device Flow auth endpoints (host: https://github.com/).
 *
 * §1.1: the `clientId` here is the USER's own OAuth App client ID; we do
 * not ship one. The user pastes it into Settings after registering their
 * OAuth App at github.com/settings/applications/new.
 */
interface GitHubAuthApi {

    @FormUrlEncoded
    @POST("login/device/code")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String,
        @Header("Accept") accept: String = "application/json",
    ): DeviceCodeResponse

    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun pollAccessToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:device_code",
        @Header("Accept") accept: String = "application/json",
    ): AccessTokenResponse
}

data class DeviceCodeResponse(
    @Json(name = "device_code")      val deviceCode: String? = null,
    @Json(name = "user_code")        val userCode: String? = null,
    @Json(name = "verification_uri") val verificationUri: String? = null,
    @Json(name = "expires_in")       val expiresIn: Int? = null,
    @Json(name = "interval")         val intervalSeconds: Int? = null,
)

/**
 * GitHub returns the same shape for success and pending; check `error` first.
 *
 * `authorization_pending` — keep polling.
 * `slow_down` — increase polling interval by 5s.
 * `access_denied` — user clicked Deny.
 * `expired_token` — user took too long; restart.
 */
data class AccessTokenResponse(
    @Json(name = "access_token")  val accessToken: String? = null,
    @Json(name = "token_type")    val tokenType: String? = null,
    @Json(name = "scope")         val scope: String? = null,
    @Json(name = "error")         val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
)
