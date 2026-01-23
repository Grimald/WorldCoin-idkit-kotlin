package com.worldcoin.idkit_kotlin.impl

import com.worldcoin.idkit_kotlin.AppError
import com.worldcoin.idkit_kotlin.CredentialCategory
import com.worldcoin.idkit_kotlin.Proof
import com.worldcoin.idkit_kotlin.impl.network.BridgeResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkSerializersTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testUUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    private val testDefaultProof = Proof.Default(
        proof = "0x123",
        merkleRoot = "0x456",
        nullifierHash = "0x789",
        credentialType = Proof.CredentialType.ORB,
        verificationLevel = Proof.CredentialType.ORB
    )

    private val testDefaultProofJson = """
            {
                "proof": "${testDefaultProof.proof}",
                "merkle_root": "${testDefaultProof.merkleRoot}",
                "nullifier_hash": "${testDefaultProof.nullifierHash}",
                "credential_type": "${testDefaultProof.credentialType.name.lowercase()}",
                "verification_level": "${testDefaultProof.verificationLevel.name.lowercase()}"
            }
        """.trimIndent()

    private val testCredentialResponse = Proof.CredentialCategory.Response(
        action = "verify",
        proof = "0xabc",
        merkleRoot = "0xdef",
        nullifierHash = "0x111",
        verificationLevel = Proof.CredentialType.ORB
    )

    private val testCredentialCategories = listOf(
        CredentialCategory.PERSONHOOD,
        CredentialCategory.DOCUMENT
    )

    private val testCredentialCategoryJson = """
            {
                "response": [
                    {
                        "action": "${testCredentialResponse.action}",
                        "proof": "${testCredentialResponse.proof}",
                        "merkle_root": "${testCredentialResponse.merkleRoot}",
                        "nullifier_hash": "${testCredentialResponse.nullifierHash}",
                        "verification_level": "${testCredentialResponse.verificationLevel.name.lowercase()}"
                    }
                ],
                "query": ${
        testCredentialCategories.joinToString(
            prefix = "[\"",
            separator = "\", \"",
            postfix = "\"]"
        ) { it.name.lowercase() }
    }
            }
        """.trimIndent()

    private val testMultipleResponses = listOf(
        testCredentialResponse,
        testCredentialResponse
    )

    private val testMultipleResponsesJson = """
            {
                "response": [
                    ${
        testMultipleResponses.joinToString(",\n                    ") { response ->
            """
                        {
                            "action": "${response.action}",
                            "proof": "${response.proof}",
                            "merkle_root": "${response.merkleRoot}",
                            "nullifier_hash": "${response.nullifierHash}",
                            "verification_level": "${response.verificationLevel.name.lowercase()}"
                        }
                        """.trimIndent()
        }
    }
                ],
                "query": ${
        testCredentialCategories.joinToString(
            prefix = "[\"",
            separator = "\", \"",
            postfix = "\"]"
        ) { it.name.lowercase() }
    }
            }
        """.trimIndent()

    private val testErrorResponse = AppError.VerificationRejected
    private val testErrorJson = """
            {
                "error_code": "verification_rejected"
            }
        """.trimIndent()

    // ========== UUIDSerializer Tests ==========

    @Test
    fun `UUIDSerializer serializes UUID to string`() {
        val jsonString = json.encodeToString(UUIDSerializer, testUUID)

        assertEquals("\"$testUUID\"", jsonString)
    }

    @Test
    fun `UUIDSerializer deserializes string to UUID`() {
        val jsonString = "\"$testUUID\""
        val uuid = json.decodeFromString(UUIDSerializer, jsonString)

        assertEquals(testUUID, uuid)
    }

    @Test
    fun `UUIDSerializer round trip preserves UUID`() {
        val original = UUID.randomUUID()
        val jsonString = json.encodeToString(UUIDSerializer, original)
        val deserialized = json.decodeFromString(UUIDSerializer, jsonString)

        assertEquals(original, deserialized)
    }

    // ========== BridgeResponseSerializer Tests ==========

    @Test
    fun `BridgeResponseSerializer deserializes Success with WorldIDProof`() {
        val response = json.decodeFromString<BridgeResponse>(testDefaultProofJson)

        assertTrue(response is BridgeResponse.Success)
        val proof = response.proof
        assertTrue(proof is Proof.Default)

        assertEquals(testDefaultProof, proof)
    }

    @Test
    fun `BridgeResponseSerializer deserializes Success with CredentialCategoryProof single response`() {
        val response = json.decodeFromString<BridgeResponse>(testCredentialCategoryJson)

        assertTrue(response is BridgeResponse.Success)
        val proof = response.proof
        assertTrue(proof is Proof.CredentialCategory)

        assertEquals(listOf(testCredentialResponse), proof.response)
        assertEquals(testCredentialCategories, proof.credentialCategory)
    }

    @Test
    fun `BridgeResponseSerializer deserializes Success with CredentialCategoryProof multiple responses`() {
        val response = json.decodeFromString<BridgeResponse>(testMultipleResponsesJson)

        assertTrue(response is BridgeResponse.Success)
        val proof = response.proof
        assertTrue(proof is Proof.CredentialCategory)

        assertEquals(testMultipleResponses, proof.response)
        assertEquals(testCredentialCategories, proof.credentialCategory)
    }

    @Test
    fun `BridgeResponseSerializer deserializes Error response`() {
        val response = json.decodeFromString<BridgeResponse>(testErrorJson)

        assertTrue(response is BridgeResponse.Error)
        assertEquals(testErrorResponse, response.error)
    }
}
