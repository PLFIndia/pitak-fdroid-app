package dev.khoj.pitaka.data.publish.github

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Orchestrates GitHub Device Flow authentication.
 *
 * D13: User registers their own OAuth App and supplies the Client ID.
 * §1.1: zero developer infrastructure.
 *
 * Usage:
 *   `start(clientId)` requests a device+user code; the screen displays the
 *   user code and routes the user to the verification URL in their browser,
 *   then collects the resulting flow:
 *
 *     Start → AwaitingUser(userCode, verificationUri)
 *     ... user authorizes in their browser ...
 *     → Success(accessToken)   OR   Denied / Expired / Failed
 */
class GitHubDeviceFlow @Inject constructor(
    private val api: GitHubAuthApi,
) {
    /** Default polling interval if GitHub doesn't return one. */
    private val defaultIntervalSeconds = 5

    sealed interface FlowState {
        data object Starting : FlowState
        data class AwaitingUser(
            val userCode: String,
            val verificationUri: String,
            val expiresInSeconds: Int,
        ) : FlowState
        data class Success(val accessToken: String, val scope: String) : FlowState
        data object Denied : FlowState
        data object Expired : FlowState
        data class Failed(val reason: String) : FlowState
    }

    fun start(
        clientId: String,
        scope: String = DEFAULT_SCOPE,
    ): Flow<FlowState> = flow {
        emit(FlowState.Starting)
        val device = try {
            api.requestDeviceCode(clientId = clientId, scope = scope)
        } catch (t: Throwable) {
            emit(FlowState.Failed(t.message ?: "Failed to start device flow"))
            return@flow
        }
        val deviceCode = device.deviceCode
        val userCode = device.userCode
        val uri = device.verificationUri
        if (deviceCode.isNullOrBlank() || userCode.isNullOrBlank() || uri.isNullOrBlank()) {
            emit(FlowState.Failed("Malformed device-code response from GitHub"))
            return@flow
        }
        emit(FlowState.AwaitingUser(userCode, uri, device.expiresIn ?: 900))

        var intervalMs = (device.intervalSeconds ?: defaultIntervalSeconds) * 1000L
        val deadlineMs = System.currentTimeMillis() + ((device.expiresIn ?: 900) * 1000L)

        while (System.currentTimeMillis() < deadlineMs) {
            delay(intervalMs)
            val r = try {
                api.pollAccessToken(clientId = clientId, deviceCode = deviceCode)
            } catch (t: Throwable) {
                emit(FlowState.Failed(t.message ?: "Polling error"))
                return@flow
            }
            if (!r.accessToken.isNullOrBlank()) {
                emit(FlowState.Success(r.accessToken, r.scope.orEmpty()))
                return@flow
            }
            when (r.error) {
                "authorization_pending" -> Unit // keep waiting
                "slow_down" -> intervalMs += 5_000L
                "expired_token" -> {
                    emit(FlowState.Expired); return@flow
                }
                "access_denied" -> {
                    emit(FlowState.Denied); return@flow
                }
                null -> Unit // shouldn't happen, but keep polling
                else -> {
                    emit(FlowState.Failed(r.errorDescription ?: r.error))
                    return@flow
                }
            }
        }
        emit(FlowState.Expired)
    }

    companion object {
        /** `public_repo` is sufficient for publishing to a user-owned public repo (D13, §1.1). */
        const val DEFAULT_SCOPE = "public_repo"
    }
}
