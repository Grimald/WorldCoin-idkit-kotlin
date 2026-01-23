package com.worldcoin.idkit_kotlin.impl.network

import com.worldcoin.idkit_kotlin.AppError
import com.worldcoin.idkit_kotlin.Proof
import com.worldcoin.idkit_kotlin.impl.BridgeResponseSerializer
import com.worldcoin.idkit_kotlin.impl.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Network-related types for communication with the World ID bridge server.
 * These types handle HTTP requests/responses and bridge server communication.
 */

/**
 * Encrypted payload container for network transmission.
 * Contains the initialization vector (IV) and the encrypted data.
 */
@Serializable
internal data class EncryptedPayload(
    @SerialName("iv") val iv: String,
    @SerialName("payload") val payload: String,
)

@Serializable(with = BridgeResponseSerializer::class)
internal sealed class BridgeResponse {

    @Serializable
    data class Success(val proof: Proof) : BridgeResponse()

    @Serializable
    data class Error(val error: AppError) : BridgeResponse()
}

/**
 * Response from creating a new verification request.
 * 
 * @param requestId The unique identifier for the created request
 */
@Serializable
internal data class CreateRequestResponse(
    @SerialName("request_id")
    @Serializable(with = UUIDSerializer::class) 
    val requestId: UUID
)

/**
 * Response from querying the status of a verification request.
 * 
 * @param status The current status of the request
 * @param response Optional encrypted response data (present when status is "completed")
 */
@Serializable
internal data class BridgeQueryResponse(val status: String, val response: EncryptedPayload?)
