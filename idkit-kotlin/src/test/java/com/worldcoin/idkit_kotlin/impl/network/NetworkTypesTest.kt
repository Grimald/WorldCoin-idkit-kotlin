package com.worldcoin.idkit_kotlin.impl.network

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NetworkTypesTest {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val uuid = UUID.randomUUID()
    private val testPayload = EncryptedPayload(
        iv = "test_iv",
        payload = "test_payload"
    )

    // ========== CreateRequestResponse Tests ==========
    
    @Test
    fun `CreateRequestResponse deserializes with UUID`() {
        val jsonString = """{"request_id":"$uuid"}"""
        
        val response = json.decodeFromString<CreateRequestResponse>(jsonString)
        
        assertEquals(uuid, response.requestId)
    }
    
    @Test
    fun `CreateRequestResponse serializes correctly`() {
        val response = CreateRequestResponse(uuid)
        
        val jsonString = json.encodeToString<CreateRequestResponse>(response)
        
        assertEquals("""{"request_id":"$uuid"}""", jsonString)
    }
    
    // ========== BridgeQueryResponse Tests ==========
    
    @Test
    fun `BridgeQueryResponse deserializes initialized status without payload`() {
        val jsonString = """{"status":"initialized","response":null}"""
        
        val response = json.decodeFromString<BridgeQueryResponse>(jsonString)
        
        assertEquals("initialized", response.status)
        assertNull(response.response)
    }
    
    @Test
    fun `BridgeQueryResponse deserializes retrieved status without payload`() {
        val jsonString = """{"status":"retrieved","response":null}"""
        
        val response = json.decodeFromString<BridgeQueryResponse>(jsonString)
        
        assertEquals("retrieved", response.status)
        assertNull(response.response)
    }
    
    @Test
    fun `BridgeQueryResponse deserializes completed status with payload`() {
        val jsonString = """
            {
                "status":"completed",
                "response":{
                    "iv":"${testPayload.iv}",
                    "payload":"${testPayload.payload}"
                }
            }
        """.trimIndent()
        
        val response = json.decodeFromString<BridgeQueryResponse>(jsonString)
        
        assertEquals("completed", response.status)
        assertNotNull(response.response)
        assertEquals(testPayload, response.response)
    }
    
    @Test
    fun `BridgeQueryResponse serializes correctly`() {
        val response = BridgeQueryResponse(
            status = "completed",
            response = testPayload
        )
        
        val jsonString = json.encodeToString<BridgeQueryResponse>(response)
        
        // Verify JSON structure
        val deserialized = json.decodeFromString<BridgeQueryResponse>(jsonString)
        assertEquals("completed", deserialized.status)
        assertEquals(testPayload.iv, deserialized.response?.iv)
        assertEquals(testPayload.payload, deserialized.response?.payload)
    }
    
    // ========== EncryptedPayload Tests ==========
    
    @Test
    fun `EncryptedPayload serializes with correct field names`() {
        val jsonString = json.encodeToString<EncryptedPayload>(testPayload)
        
        assertEquals("""{"iv":"${testPayload.iv}","payload":"${testPayload.payload}"}""", jsonString)
    }
    
    @Test
    fun `EncryptedPayload deserializes correctly`() {
        val jsonString = """{"iv":"${testPayload.iv}","payload":"test_data"}"""
        
        val payload = json.decodeFromString<EncryptedPayload>(jsonString)
        
        assertEquals(testPayload.iv, payload.iv)
        assertEquals("test_data", payload.payload)
    }
    
    @Test
    fun `EncryptedPayload round trip preserves data`() {
        val jsonString = json.encodeToString<EncryptedPayload>(testPayload)
        val deserialized = json.decodeFromString<EncryptedPayload>(jsonString)
        
        assertEquals(testPayload, deserialized)
    }
}
