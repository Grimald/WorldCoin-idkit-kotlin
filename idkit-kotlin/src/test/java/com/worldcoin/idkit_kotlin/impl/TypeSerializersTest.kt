package com.worldcoin.idkit_kotlin.impl

import com.worldcoin.idkit_kotlin.AppError
import com.worldcoin.idkit_kotlin.AppID
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeSerializersTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ========== AppIDSerializer Tests ==========

    @Test
    fun `AppIDSerializer serializes AppID to string`() {
        val appId = AppID("app_test123")
        val jsonString = json.encodeToString(AppIDSerializer, appId)

        assertEquals("\"app_test123\"", jsonString)
    }

    @Test
    fun `AppIDSerializer deserializes string to AppID`() {
        val jsonString = "\"app_test123\""
        val appId = json.decodeFromString(AppIDSerializer, jsonString)

        assertEquals("app_test123", appId.rawId)
    }

    @Test
    fun `AppIDSerializer round trip preserves value`() {
        val original = AppID("app_staging_456")
        val jsonString = json.encodeToString(AppIDSerializer, original)
        val deserialized = json.decodeFromString(AppIDSerializer, jsonString)

        assertEquals(original.rawId, deserialized.rawId)
    }

    // ========== AppErrorSerializer Tests ==========

    @Test
    fun `AppErrorSerializer deserializes connection_failed`() {
        val jsonString = "\"connection_failed\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.ConnectionFailed, error)
    }

    @Test
    fun `AppErrorSerializer deserializes verification_rejected`() {
        val jsonString = "\"verification_rejected\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.VerificationRejected, error)
    }

    @Test
    fun `AppErrorSerializer deserializes max_verifications_reached`() {
        val jsonString = "\"max_verifications_reached\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.MaxVerificationsReached, error)
    }

    @Test
    fun `AppErrorSerializer deserializes credential_unavailable`() {
        val jsonString = "\"credential_unavailable\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.CredentialUnavailable, error)
    }

    @Test
    fun `AppErrorSerializer deserializes malformed_request`() {
        val jsonString = "\"malformed_request\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.MalformedRequest, error)
    }

    @Test
    fun `AppErrorSerializer deserializes invalid_network`() {
        val jsonString = "\"invalid_network\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.InvalidNetwork, error)
    }

    @Test
    fun `AppErrorSerializer deserializes inclusion_proof_failed`() {
        val jsonString = "\"inclusion_proof_failed\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.InclusionProofFailed, error)
    }

    @Test
    fun `AppErrorSerializer deserializes inclusion_proof_pending`() {
        val jsonString = "\"inclusion_proof_pending\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.InclusionProofPending, error)
    }

    @Test
    fun `AppErrorSerializer deserializes unexpected_response`() {
        val jsonString = "\"unexpected_response\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.UnexpectedResponse, error)
    }

    @Test
    fun `AppErrorSerializer deserializes failed_by_host_app`() {
        val jsonString = "\"failed_by_host_app\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertEquals(AppError.FailedByHostApp, error)
    }

    @Test
    fun `AppErrorSerializer deserializes generic_error`() {
        val jsonString = "\"generic_error\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertTrue(error is AppError.GenericError)
    }

    @Test
    fun `AppErrorSerializer deserializes unknown error as GenericError`() {
        val jsonString = "\"unknown_error_type\""
        val error = json.decodeFromString(AppErrorSerializer, jsonString)

        assertTrue(error is AppError.GenericError)
        // GenericError.reason should contain the unknown error string
        assertTrue(
            error.reason?.contains("unknown_error_type") == true ||
                    error.message.contains("unknown_error_type")
        )
    }

    @Test
    fun `AppErrorSerializer serializes ConnectionFailed`() {
        val error = AppError.ConnectionFailed
        val jsonString = json.encodeToString(AppErrorSerializer, error)

        // Message should be serialized
        assertTrue(jsonString.contains("Failed to connect"))
    }
}
