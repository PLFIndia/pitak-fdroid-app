package dev.khoj.pitaka.data.publish.github

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class GitHubDeviceFlowTest {

    private lateinit var server: MockWebServer
    private lateinit var flow: GitHubDeviceFlow

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubAuthApi::class.java)
        flow = GitHubDeviceFlow(api)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun happy_path_emits_AwaitingUser_then_Success() = runBlocking {
        server.enqueue(MockResponse().setBody(DEVICE_CODE_BODY_INTERVAL_1))
        server.enqueue(MockResponse().setBody(SUCCESS_BODY))

        flow.start(clientId = "abc").test {
            assertThat(awaitItem()).isEqualTo(GitHubDeviceFlow.FlowState.Starting)
            val awaiting = awaitItem() as GitHubDeviceFlow.FlowState.AwaitingUser
            assertThat(awaiting.userCode).isEqualTo("ABCD-1234")
            assertThat(awaiting.verificationUri).contains("github.com")
            val success = awaitItem() as GitHubDeviceFlow.FlowState.Success
            assertThat(success.accessToken).isEqualTo("ghu_xxx")
            awaitComplete()
        }
    }

    @Test
    fun authorization_pending_keeps_polling_until_success() = runBlocking {
        server.enqueue(MockResponse().setBody(DEVICE_CODE_BODY_INTERVAL_1))
        server.enqueue(MockResponse().setBody("""{"error":"authorization_pending"}"""))
        server.enqueue(MockResponse().setBody(SUCCESS_BODY))

        flow.start(clientId = "abc").test {
            awaitItem() // Starting
            awaitItem() // AwaitingUser
            val success = awaitItem() as GitHubDeviceFlow.FlowState.Success
            assertThat(success.accessToken).isEqualTo("ghu_xxx")
            awaitComplete()
        }
    }

    @Test
    fun denied_emits_Denied_and_stops() = runBlocking {
        server.enqueue(MockResponse().setBody(DEVICE_CODE_BODY_INTERVAL_1))
        server.enqueue(MockResponse().setBody("""{"error":"access_denied"}"""))

        flow.start(clientId = "abc").test {
            awaitItem() // Starting
            awaitItem() // AwaitingUser
            assertThat(awaitItem()).isEqualTo(GitHubDeviceFlow.FlowState.Denied)
            awaitComplete()
        }
    }

    @Test
    fun malformed_device_code_emits_Failed() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user_code":null}"""))
        flow.start(clientId = "abc").test {
            awaitItem() // Starting
            val failed = awaitItem() as GitHubDeviceFlow.FlowState.Failed
            assertThat(failed.reason).contains("Malformed")
            awaitComplete()
        }
    }
}

private val DEVICE_CODE_BODY_INTERVAL_1 = """
{
  "device_code": "dc-1",
  "user_code": "ABCD-1234",
  "verification_uri": "https://github.com/login/device",
  "expires_in": 900,
  "interval": 1
}
""".trimIndent()

private val SUCCESS_BODY = """
{ "access_token": "ghu_xxx", "token_type": "bearer", "scope": "public_repo" }
""".trimIndent()
