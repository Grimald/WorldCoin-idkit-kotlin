package com.worldcoin.idkit_kotlin.impl.session

import com.worldcoin.idkit_kotlin.*
import com.worldcoin.idkit_kotlin.impl.Crypto
import com.worldcoin.idkit_kotlin.impl.network.BridgeQueryResponse
import com.worldcoin.idkit_kotlin.impl.network.BridgeResponse
import com.worldcoin.idkit_kotlin.impl.network.EncryptedPayload
import com.worldcoin.idkit_kotlin.impl.network.NetworkClient
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.UUID
import javax.crypto.SecretKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultSessionTest {

    private val testRequestId = UUID.randomUUID()
    private val testBridgeUrl = BridgeURL.default
    private val testCustomBridgeUrl = BridgeURL("https://custom.bridge.com")

    private val testProof = Proof.Default(
        merkleRoot = "0x123",
        nullifierHash = "0x456",
        proof = "0x789",
        credentialType = Proof.CredentialType.ORB,
        verificationLevel = Proof.CredentialType.ORB
    )

    private val testKey = Crypto.generateAESKey()
    
    private val session by lazy {
        DefaultSession(
            requestID = testRequestId,
            key = testKey,
            bridgeURL = testBridgeUrl,
            connectUrlType = ConnectUrlType.WLD
        )
    }

    private val encryptedPayload = EncryptedPayload(iv = "test_iv", payload = "test_payload")
    private val bridgeQueryResponse = BridgeQueryResponse(status = "completed", response = encryptedPayload)

    @BeforeEach
    fun setUp() {
        mockkObject(NetworkClient)
        mockkObject(Crypto)
        
        // Mock Android Log to prevent "Method w in android.util.Log not mocked" error
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ========== connectUrl Tests ==========

    @Test
    fun `connectUrl contains all required parameters`() {
        val url = session.connectUrl

        assertTrue(url.toString().contains("worldcoin.org/verify"))
        assertTrue(url.query.contains("t=wld"))
        assertTrue(url.query.contains("i=$testRequestId"))
        assertTrue(url.query.contains("k="))
    }

    @Test
    fun `connectUrl uses CREDENTIAL_CATEGORY type for credential category sessions`() {
        val credentialSession = DefaultSession(
            requestID = testRequestId,
            key = testKey,
            bridgeURL = testBridgeUrl,
            connectUrlType = ConnectUrlType.CREDENTIAL_CATEGORY
        )

        val url = credentialSession.connectUrl

        assertTrue(url.query.contains("t=cred"))
    }

    @Test
    fun `connectUrl excludes bridge URL parameter when using default bridge`() {
        val url = session.connectUrl

        assertTrue(!url.query.contains("b="))
    }

    @Test
    fun `connectUrl includes bridge URL parameter when using custom bridge`() {
        val customSession = DefaultSession(
            requestID = testRequestId,
            key = testKey,
            bridgeURL = testCustomBridgeUrl,
            connectUrlType = ConnectUrlType.WLD
        )

        val url = customSession.connectUrl

        assertTrue(url.query.contains("b="))
        assertTrue(url.query.contains("custom.bridge.com"))
    }

    @Test
    fun `connectUrl URL-encodes parameters correctly`() {
        val url = session.connectUrl

        // Verify URL is valid
        assertNotNull(url)
        assertEquals("https", url.protocol)
        assertEquals("worldcoin.org", url.host)
        assertEquals("/verify", url.path)
    }

    // ========== status() Flow Tests - Initial State ==========

    @Test
    fun `status emits WaitingForConnection initially`() = runTest {
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } returns BridgeQueryResponse(status = "initialized", response = null)

        val firstStatus = session.status().first()

        assertEquals(Status.WaitingForConnection, firstStatus)
    }

    // ========== status() Flow Tests - Terminal States ==========

    @Test
    fun `status emits Confirmed with proof on completed success response`() = runTest {
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } returns bridgeQueryResponse
        
        coEvery {
            Crypto.decryptPayload(encryptedPayload, testKey)
        } returns BridgeResponse.Success(testProof)

        val statuses = session.status().toList()

        // Should have WaitingForConnection and Confirmed
        assertTrue(statuses.size >= 2)
        assertEquals(Status.WaitingForConnection, statuses[0])
        assertTrue(statuses.last() is Status.Confirmed)
        
        val confirmedStatus = statuses.last() as Status.Confirmed
        assertEquals(testProof, confirmedStatus.proof)
    }

    @Test
    fun `status emits Failed on completed error response`() = runTest {
        val error = AppError.VerificationRejected
        
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } returns bridgeQueryResponse
        
        coEvery {
            Crypto.decryptPayload(encryptedPayload, testKey)
        } returns BridgeResponse.Error(error)

        val statuses = session.status().toList()

        assertTrue(statuses.size >= 2)
        assertEquals(Status.WaitingForConnection, statuses[0])
        assertTrue(statuses.last() is Status.Failed)
        
        val failedStatus = statuses.last() as Status.Failed
        assertEquals(error, failedStatus.error)
    }

    // ========== status() Flow Tests - Error Handling ==========

    @Test
    fun `status continues polling after IOException until success`() = runTest {
        // First 2 polls throw IOException, 3rd returns retrieved, 4th returns completed
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } throws IOException("Network error") andThenThrows
          IOException("Network error") andThen
          BridgeQueryResponse(status = "retrieved", response = null) andThen
          BridgeQueryResponse(status = "completed", response = encryptedPayload)
        
        coEvery {
            Crypto.decryptPayload(encryptedPayload, testKey)
        } returns BridgeResponse.Success(testProof)

        val statuses = session.status().toList()

        // Should emit WaitingForConnection, AwaitingConfirmation, Confirmed
        // (IOException is retried silently)
        assertTrue(statuses.size >= 3)
        assertEquals(Status.WaitingForConnection, statuses[0])
        assertTrue(statuses[1] is Status.AwaitingConfirmation)
        assertTrue(statuses.last() is Status.Confirmed)
    }

    @Test
    fun `status propagates CancellationException`() = runTest {
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } throws CancellationException("Test cancellation")

        assertFailsWith<CancellationException> {
            session.status().collect { }
        }
    }

    @Test
    fun `status wraps AppErrorThrowable in Failed status`() = runTest {
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } throws AppErrorThrowable(AppError.UnexpectedResponse)

        val statuses = session.status().toList()

        assertTrue(statuses.size >= 2)
        assertEquals(Status.WaitingForConnection, statuses[0])
        assertTrue(statuses.last() is Status.Failed)
        
        val failedStatus = statuses.last() as Status.Failed
        assertEquals(AppError.UnexpectedResponse, failedStatus.error)
    }

    // ========== status() Flow Tests - Polling Behavior ==========

    @Test
    fun `status stops polling after Confirmed status`() = runTest {
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } returns bridgeQueryResponse
        
        coEvery {
            Crypto.decryptPayload(encryptedPayload, testKey)
        } returns BridgeResponse.Success(testProof)

        session.status().toList()

        // Should stop after Confirmed, not continue polling
        coVerify(atMost = 2) {
            NetworkClient.queryRequest(any(), any())
        }
    }

    @Test
    fun `status stops polling after Failed status from AppErrorThrowable`() = runTest {
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } throws AppErrorThrowable(AppError.UnexpectedResponse)

        session.status().toList()

        // Should stop after Failed, not continue polling
        coVerify(atMost = 2) {
            NetworkClient.queryRequest(any(), any())
        }
    }

    // ========== status() Flow Tests - Edge Cases ==========

    @Test
    fun `status throws on unknown bridge status`() = runTest {
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } returns BridgeQueryResponse(status = "unknown_status", response = null)

        val statuses = session.status().toList()

        assertTrue(statuses.size >= 2)
        assertEquals(Status.WaitingForConnection, statuses[0])
        assertTrue(statuses.last() is Status.Failed)
        
        val failedStatus = statuses.last() as Status.Failed
        assertEquals(AppError.UnexpectedResponse, failedStatus.error)
    }

    @Test
    fun `status throws when completed but no payload`() = runTest {
        coEvery {
            NetworkClient.queryRequest(any(), any())
        } returns BridgeQueryResponse(status = "completed", response = null)

        val statuses = session.status().toList()

        assertTrue(statuses.size >= 2)
        assertEquals(Status.WaitingForConnection, statuses[0])
        assertTrue(statuses.last() is Status.Failed)
    }
}
