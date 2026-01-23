package com.worldcoin.idkit_kotlin.impl.network

import com.worldcoin.idkit_kotlin.BridgeURL
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow

private const val DEFAULT_HTTP_CONNECT_TIMEOUT_SEC = 10L
private const val DEFAULT_HTTP_READ_TIMEOUT_SEC = 15L
private const val MAX_HTTP_RETRY_ATTEMPTS = 3
private const val HTTP_RETRY_BASE_DELAY_MS = 1000L

// HTTP headers
private const val HTTP_HEADER_USER_AGENT = "User-Agent"

// HTTP header values
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val HTTP_USER_AGENT = "idkit-kotlin"

/**
 * HTTP client interface for network operations.
 */
internal interface HttpClient {
    /**
     * Executes a POST request with JSON payload.
     * May throw IOException if the request fails after all retry attempts.
     *
     * @param url The target URL
     * @param payload The JSON payload to send
     * @return The response body as a string
     */
    suspend fun post(url: String, payload: String): String

    /**
     * Executes a GET request.
     * May throw IOException if the request fails after all retry attempts.
     *
     * @param url The target URL
     * @return The response body as a string
     */
    suspend fun get(url: String): String
}

/**
 * Default implementation of HttpClient using OkHttp.
 */
internal class DefaultHttpClient(
    connectTimeoutSec: Long = DEFAULT_HTTP_CONNECT_TIMEOUT_SEC,
    readTimeoutSec: Long = DEFAULT_HTTP_READ_TIMEOUT_SEC
) : HttpClient {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(connectTimeoutSec + readTimeoutSec, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun post(url: String, payload: String): String {
        return executeWithRetry(url) {
            val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header(HTTP_HEADER_USER_AGENT, HTTP_USER_AGENT)
                .build()

            executeRequest(request, url)
        }
    }

    override suspend fun get(url: String): String {
        return executeWithRetry(url) {
            val request = Request.Builder()
                .url(url)
                .get()
                .header(HTTP_HEADER_USER_AGENT, HTTP_USER_AGENT)
                .build()

            executeRequest(request, url)
        }
    }

    /**
     * Executes an HTTP request with automatic retry and exponential backoff strategy: `delay = base * 2^attempt`.
     *
     * @param url The URL being requested (for logging)
     * @param maxAttempts Maximum number of retry attempts
     * @param block The request logic to execute
     * @return The result from the request
     */
    private suspend fun <T> executeWithRetry(
        url: String,
        maxAttempts: Int = MAX_HTTP_RETRY_ATTEMPTS,
        block: suspend () -> T
    ): T {
        var lastException: IOException? = null

        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                lastException = e
                val attemptNumber = attempt + 1

                if (attemptNumber < maxAttempts) {
                    val delayMs = (HTTP_RETRY_BASE_DELAY_MS * 2.0.pow(attempt)).toLong()
                    delay(delayMs)
                }
            }
        }

        throw lastException ?: IOException("Request to $url failed after $maxAttempts attempts")
    }

    /**
     * Executes an OkHttp request as a suspending function.
     * Converts OkHttp's async callback API to Kotlin coroutines.
     *
     * @param request The OkHttp request to execute
     * @param url The URL being requested (for error messages)
     * @return The response body as a string
     */
    private suspend fun executeRequest(request: Request, url: String): String =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)

            // Cancel OkHttp call if coroutine is cancelled
            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body.string()
                            if (body.isEmpty()) {
                                continuation.resumeWithException(
                                    IOException("Received empty response body from $url")
                                )
                            } else {
                                continuation.resume(body)
                            }
                        } else {
                            val errorBody = resp.body.string()
                            continuation.resumeWithException(
                                IOException("HTTP ${resp.code} ${resp.message} from $url: $errorBody")
                            )
                        }
                    }
                }
            })
        }
}

/**
 * Network client for communicating with the World ID bridge server.
 *
 * Handles HTTP requests/responses with automatic retry logic and proper error handling.
 * All methods may throw IOException if the network request fails after all retry attempts.
 */
internal object NetworkClient {
    private val httpClient: HttpClient = DefaultHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Creates a new verification request on the bridge server.
     * May throw IOException if the network request fails.
     *
     * @param data The encrypted payload to send
     * @param bridgeURL The bridge server URL
     * @return The response containing the request ID
     */
    suspend fun createRequest(data: EncryptedPayload, bridgeURL: BridgeURL): CreateRequestResponse {
        val url = "${bridgeURL.rawURL}/request"
        val payload = json.encodeToString(data)
        val response = httpClient.post(url, payload)

        return json.decodeFromString(response)
    }

    /**
     * Queries the status of a verification request.
     * May throw IOException if the network request fails.
     *
     * @param requestId The ID of the request to query
     * @param bridgeURL The bridge server URL
     * @return The response containing the current status
     */
    suspend fun queryRequest(requestId: UUID, bridgeURL: BridgeURL): BridgeQueryResponse {
        val url = "${bridgeURL.rawURL}/response/$requestId"
        val response = httpClient.get(url)

        return json.decodeFromString(response)
    }
}
