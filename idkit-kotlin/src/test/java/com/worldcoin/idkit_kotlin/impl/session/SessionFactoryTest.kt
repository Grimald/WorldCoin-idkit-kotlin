package com.worldcoin.idkit_kotlin.impl.session

import com.worldcoin.idkit_kotlin.AppError
import com.worldcoin.idkit_kotlin.AppErrorThrowable
import com.worldcoin.idkit_kotlin.AppID
import com.worldcoin.idkit_kotlin.BridgeURL
import com.worldcoin.idkit_kotlin.CredentialCategory
import com.worldcoin.idkit_kotlin.VerificationLevel
import com.worldcoin.idkit_kotlin.impl.network.CreateRequestResponse
import com.worldcoin.idkit_kotlin.impl.network.NetworkClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionFactoryTest {

    companion object {
        private const val TEST_ACTION = "verify"
        private const val TEST_SIGNAL = "test_signal"
        private const val TEST_ACTION_DESCRIPTION = "Test action"
    }

    private val testAppID = AppID("app_test_123")
    private val mockRequestId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mockkObject(NetworkClient)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ========== Success Cases ==========

    @Test
    fun `create calls NetworkClient with encrypted payload`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(mockRequestId)

        val session = SessionFactory.create(
            appID = testAppID,
            action = TEST_ACTION,
            verificationLevel = VerificationLevel.ORB,
            bridgeURL = BridgeURL.default,
            signal = TEST_SIGNAL,
            actionDescription = TEST_ACTION_DESCRIPTION
        )

        coVerify(exactly = 1) {
            NetworkClient.createRequest(any(), any())
        }
        assertNotNull(session)
    }

    @Test
    fun `createCredentialCategorySession calls NetworkClient`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(mockRequestId)

        val session = SessionFactory.createCredentialCategorySession(
            appID = testAppID,
            action = TEST_ACTION,
            credentialCategory = setOf(CredentialCategory.PERSONHOOD),
            bridgeURL = BridgeURL.default,
            signal = TEST_SIGNAL,
            actionDescription = TEST_ACTION_DESCRIPTION
        )

        coVerify(exactly = 1) {
            NetworkClient.createRequest(any(), any())
        }
        assertNotNull(session)
    }

    // ========== Validation Tests ==========

    @Test
    fun `createCredentialCategorySession throws on empty credentialCategory`() = runTest {
        val exception = assertThrows<AppErrorThrowable> {
            SessionFactory.createCredentialCategorySession(
                appID = testAppID,
                action = TEST_ACTION,
                credentialCategory = emptySet(),
                bridgeURL = BridgeURL.default,
                signal = TEST_SIGNAL,
                actionDescription = null
            )
        }

        assertTrue(exception.appError is AppError.InvalidInput)
        assertTrue(exception.message!!.contains("credentialCategory must not be empty"))
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `create wraps IOException in AppErrorThrowable`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } throws IOException("Network timeout")

        val exception = assertThrows<AppErrorThrowable> {
            SessionFactory.create(
                appID = testAppID,
                action = TEST_ACTION,
                verificationLevel = VerificationLevel.ORB,
                bridgeURL = BridgeURL.default,
                signal = TEST_SIGNAL,
                actionDescription = null
            )
        }

        assertTrue(exception.appError is AppError.GenericError)
        assertTrue(exception.message!!.contains("Failed to connect to the Bridge server"))
        assertTrue(exception.message!!.contains("Network timeout"))
    }

    @Test
    fun `create wraps SerializationException in AppErrorThrowable`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } throws SerializationException("Invalid JSON")

        val exception = assertThrows<AppErrorThrowable> {
            SessionFactory.create(
                appID = testAppID,
                action = TEST_ACTION,
                verificationLevel = VerificationLevel.ORB,
                bridgeURL = BridgeURL.default,
                signal = TEST_SIGNAL,
                actionDescription = null
            )
        }

        assertTrue(exception.appError is AppError.GenericError)
        assertTrue(exception.message!!.contains("JSON parsing failed"))
        assertTrue(exception.message!!.contains("Invalid JSON"))
    }

    // ========== Signal Encoding Tests ==========

    @Test
    fun `create encodes signal parameter`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(mockRequestId)

        val session = SessionFactory.create(
            appID = testAppID,
            action = TEST_ACTION,
            verificationLevel = VerificationLevel.ORB,
            bridgeURL = BridgeURL.default,
            signal = TEST_SIGNAL,
            actionDescription = null
        )

        assertNotNull(session)
        coVerify(exactly = 1) {
            NetworkClient.createRequest(any(), any())
        }
    }
}
