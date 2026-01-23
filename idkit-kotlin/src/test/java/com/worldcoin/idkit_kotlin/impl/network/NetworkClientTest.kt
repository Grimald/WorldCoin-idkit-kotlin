package com.worldcoin.idkit_kotlin.impl.network

import com.worldcoin.idkit_kotlin.BridgeURL
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class NetworkClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val mockWebServer = MockWebServer()
    private val bridgeURL by lazy {
        BridgeURL(mockWebServer.url("/").toString().trimEnd('/'))
    }
    private val testRequestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    private val testEncryptedPayload = EncryptedPayload(iv = "test_iv", payload = "test_payload")

    @BeforeEach
    fun setUp() {
        // Mock Android Log (required because android.util.Log is unavailable in JVM unit tests)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0

        // Start MockWebServer for each test to ensure test isolation
        mockWebServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        unmockkAll()
    }

    // ========== createRequest Tests ==========

    @Test
    fun `createRequest sends POST with encrypted payload and returns request ID`() = runTest {
        val expectedResponse = CreateRequestResponse(requestId = testRequestId)
        val responseJson = json.encodeToString(expectedResponse)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )

        val response = NetworkClient.createRequest(testEncryptedPayload, bridgeURL)

        assertEquals(testRequestId, response.requestId)

        // Verify request
        val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("POST", recordedRequest.method)
        assertEquals("/request", recordedRequest.path)
        // OkHttp adds charset to Content-Type header
        assert(recordedRequest.getHeader("Content-Type")?.startsWith("application/json") == true)
        assertEquals("idkit-kotlin", recordedRequest.getHeader("User-Agent"))

        // Verify payload was sent
        val sentPayload = json.decodeFromString<EncryptedPayload>(recordedRequest.body.readUtf8())
        assertEquals(testEncryptedPayload.iv, sentPayload.iv)
        assertEquals(testEncryptedPayload.payload, sentPayload.payload)
    }

    @Test
    fun `createRequest handles HTTP 500 error`() = runTest {
        repeat(5) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")
            )
        }

        assertFailsWith<IOException> {
            NetworkClient.createRequest(testEncryptedPayload, bridgeURL)
        }

        // Verify request was attempted with retries (MAX_HTTP_RETRY_ATTEMPTS = 3)
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `createRequest retries on network failure and succeeds`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server Error")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(CreateRequestResponse(requestId = testRequestId)))
        )

        val response = NetworkClient.createRequest(testEncryptedPayload, bridgeURL)

        assertEquals(testRequestId, response.requestId)

        // Should have made 2 requests (first failed, second succeeded)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `createRequest throws on empty response body`() = runTest {
        repeat(5) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("")
            )
        }

        assertFailsWith<IOException> {
            NetworkClient.createRequest(testEncryptedPayload, bridgeURL)
        }
    }

    // ========== queryRequest Tests ==========

    @Test
    fun `queryRequest sends GET and returns initialized status`() = runTest {
        val expectedResponse = BridgeQueryResponse(status = "initialized", response = null)
        val responseJson = json.encodeToString(expectedResponse)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )

        val response = NetworkClient.queryRequest(testRequestId, bridgeURL)

        assertEquals("initialized", response.status)
        assertEquals(null, response.response)

        // Verify request
        val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("GET", recordedRequest.method)
        assertEquals("/response/$testRequestId", recordedRequest.path)
        assertEquals("idkit-kotlin", recordedRequest.getHeader("User-Agent"))
    }

    @Test
    fun `queryRequest returns completed status with encrypted payload`() = runTest {
        val expectedResponse = BridgeQueryResponse(
            status = "completed",
            response = testEncryptedPayload
        )
        val responseJson = json.encodeToString(expectedResponse)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
        )

        val response = NetworkClient.queryRequest(testRequestId, bridgeURL)

        assertEquals("completed", response.status)
        assertNotNull(response.response)
        assertEquals(testEncryptedPayload.iv, response.response.iv)
        assertEquals(testEncryptedPayload.payload, response.response.payload)
    }

    @Test
    fun `queryRequest handles HTTP 500 error`() = runTest {
        repeat(5) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")
            )
        }

        assertFailsWith<IOException> {
            NetworkClient.queryRequest(testRequestId, bridgeURL)
        }

        // Verify request was attempted with retries (MAX_HTTP_RETRY_ATTEMPTS = 3)
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `queryRequest retries on network failure and succeeds`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server Error")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    json.encodeToString(
                        BridgeQueryResponse(
                            status = "initialized",
                            response = null
                        )
                    )
                )
        )

        val response = NetworkClient.queryRequest(testRequestId, bridgeURL)

        assertEquals("initialized", response.status)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `queryRequest throws on empty response body`() = runTest {
        repeat(5) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("")
            )
        }

        assertFailsWith<IOException> {
            NetworkClient.queryRequest(testRequestId, bridgeURL)
        }
    }
}
