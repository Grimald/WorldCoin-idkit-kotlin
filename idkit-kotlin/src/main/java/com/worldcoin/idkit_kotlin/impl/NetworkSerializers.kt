package com.worldcoin.idkit_kotlin.impl

import com.worldcoin.idkit_kotlin.AppError
import com.worldcoin.idkit_kotlin.Proof
import com.worldcoin.idkit_kotlin.impl.network.BridgeResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.UUID

internal object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

@Suppress("EXPOSED_PARAMETER_TYPE", "EXPOSED_FUNCTION_RETURN_TYPE")
internal object BridgeResponseSerializer : KSerializer<BridgeResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BridgeResponse") {
        element<Proof>("proof", isOptional = true)
        element<AppError>("error_code", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: BridgeResponse) {
        // Serialization not implemented - only used for deserialization
    }

    override fun deserialize(decoder: Decoder): BridgeResponse {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This class can be loaded only by JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return when {
            "error_code" in jsonObject -> {
                val error =
                    jsonDecoder.json.decodeFromJsonElement<AppError>(jsonObject["error_code"]!!)
                BridgeResponse.Error(error)
            }

            "proof" in jsonObject -> {
                val proof = jsonDecoder.json.decodeFromJsonElement<Proof.Default>(jsonObject)
                BridgeResponse.Success(proof)
            }

            "response" in jsonObject -> {
                val proof = jsonDecoder.json.decodeFromJsonElement<Proof.CredentialCategory>(jsonObject)
                BridgeResponse.Success(proof)
            }

            else -> throw SerializationException("BridgeResponse doesn't match any expected type")
        }
    }
}
