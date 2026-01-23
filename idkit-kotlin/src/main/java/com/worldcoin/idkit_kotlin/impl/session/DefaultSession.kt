package com.worldcoin.idkit_kotlin.impl.session

import com.worldcoin.idkit_kotlin.*
import com.worldcoin.idkit_kotlin.impl.Crypto
import com.worldcoin.idkit_kotlin.impl.logWarning
import com.worldcoin.idkit_kotlin.impl.network.BridgeQueryResponse
import com.worldcoin.idkit_kotlin.impl.network.BridgeResponse
import com.worldcoin.idkit_kotlin.impl.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Default implementation of Session interface.
 */
internal class DefaultSession(
    private val requestID: UUID,
    private val key: SecretKey,
    private val bridgeURL: BridgeURL,
    private val connectUrlType: ConnectUrlType,
    private val config: SessionConfig = SessionConfig()
) : Session {

    override val connectUrl: URL
        get() {
            val queryParams = mutableListOf(
                "t" to connectUrlType.type,
                "i" to requestID.toString(),
                "k" to Base64.getEncoder().encodeToString(key.encoded)
            )

            if (bridgeURL != BridgeURL.default) {
                queryParams.add("b" to bridgeURL.rawURL)
            }

            val queryString = queryParams.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, StandardCharsets.UTF_8.toString())}" +
                        "=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString())}"
            }

            return URL("https://worldcoin.org/verify?$queryString")
        }

    override fun status(): Flow<Status> = flow {
        var currentStatus: Status = Status.WaitingForConnection
        emit(currentStatus)

        while (true) {
            try {
                val bridgeResponse = NetworkClient.queryRequest(requestID, bridgeURL)
                val newStatus = handleBridgeResponse(bridgeResponse)
                
                if (newStatus != currentStatus) {
                    currentStatus = newStatus
                    emit(newStatus)
                }
                
                // Break on terminal statuses
                if (newStatus is Status.Failed || newStatus is Status.Confirmed) {
                    break
                }

                delay(config.intervalMs)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                // Network error - likely caused by Android blocking DNS in background.
                // Continue polling as user may complete verification and DNS may recover.
                // User can cancel via coroutine cancellation if needed.
                logWarning(message = "Network error, will continue polling: ${ex.message}")
                delay(config.intervalMs)
                continue
            } catch (ex: Exception) {
                // Permanent error (e.g., AppErrorThrowable with UnexpectedResponse)
                logWarning(message = "Something went wrong: $ex")
                val error = when (ex) {
                    is AppErrorThrowable -> ex.appError
                    else -> AppError.GenericError(ex.message)
                }
                emit(Status.Failed(error))
                break
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun handleBridgeResponse(response: BridgeQueryResponse): Status {
        val bridgeStatus = BridgeStatus.fromString(response.status) ?: run {
            logWarning(message = "Unknown bridge status: ${response.status}")
            throw AppErrorThrowable(AppError.UnexpectedResponse)
        }
        
        return when (bridgeStatus) {
            BridgeStatus.INITIALIZED -> Status.WaitingForConnection
            BridgeStatus.RETRIEVED -> Status.AwaitingConfirmation
            BridgeStatus.COMPLETED -> {
                val payload = response.response 
                    ?: throw AppErrorThrowable(AppError.UnexpectedResponse)
                when (val decryptedResponse = Crypto.decryptPayload(payload, key)) {
                    is BridgeResponse.Error -> Status.Failed(decryptedResponse.error)
                    is BridgeResponse.Success -> Status.Confirmed(decryptedResponse.proof)
                }
            }
        }
    }
}

/**
 * Configuration for session.
 *
 * @param intervalMs The polling interval in milliseconds
 */
internal data class SessionConfig(
    val intervalMs: Long = DEFAULT_SESSION_POLLING_INTERVAL_MS
) {
    companion object {
        private const val DEFAULT_SESSION_POLLING_INTERVAL_MS = 3000L
    }
}

/**
 * Bridge server status.
 */
private enum class BridgeStatus(val value: String) {
    /** Session has been initialized but not yet retrieved by the user */
    INITIALIZED("initialized"),
    
    /** Session has been retrieved by the user */
    RETRIEVED("retrieved"),
    
    /** Session has been completed with a result */
    COMPLETED("completed");

    companion object {
        fun fromString(value: String): BridgeStatus? =
            entries.find { it.value == value }
    }
}
