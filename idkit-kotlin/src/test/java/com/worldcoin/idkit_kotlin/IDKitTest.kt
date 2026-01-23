package com.worldcoin.idkit_kotlin

import com.worldcoin.idkit_kotlin.impl.network.CreateRequestResponse
import com.worldcoin.idkit_kotlin.impl.network.NetworkClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IDKitTest {

    companion object {
        private val testAppID = AppID("app_test_123")
        private const val TEST_ACTION = "verify"
        private const val TEST_SIGNAL = "test_signal"
        private const val TEST_ACTION_DESCRIPTION = "Test action"
    }

    private val testCredentialCategory = setOf(CredentialCategory.PERSONHOOD)

    @BeforeEach
    fun setUp() {
        mockkObject(NetworkClient)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ========== createSession Tests ==========

    @Test
    fun `createSession returns Session with valid parameters`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(UUID.randomUUID())

        val session = IDKit.createSession(
            appID = testAppID,
            action = TEST_ACTION,
            verificationLevel = VerificationLevel.ORB,
            bridgeURL = BridgeURL.default,
            signal = TEST_SIGNAL,
            actionDescription = TEST_ACTION_DESCRIPTION
        )

        assertNotNull(session)
        assertNotNull(session.connectUrl)
    }

    @Test
    fun `createSession uses default parameters`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(UUID.randomUUID())

        // Only required parameters
        val session = IDKit.createSession(
            appID = testAppID,
            action = TEST_ACTION
        )

        assertNotNull(session)
    }

    @Test
    fun `createSession throws AppErrorThrowable on network failure`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } throws IOException("Connection failed")

        val exception = assertThrows<AppErrorThrowable> {
            IDKit.createSession(
                appID = testAppID,
                action = TEST_ACTION
            )
        }

        assertTrue(exception.appError is AppError.GenericError)
        assertTrue(exception.message!!.contains("Failed to connect"))
    }

    @Test
    fun `createSession works with all verification levels`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(UUID.randomUUID())

        // Test ORB
        IDKit.createSession(
            appID = testAppID,
            action = TEST_ACTION,
            verificationLevel = VerificationLevel.ORB
        )

        // Test DEVICE
        IDKit.createSession(
            appID = testAppID,
            action = TEST_ACTION,
            verificationLevel = VerificationLevel.DEVICE
        )

        // Test DOCUMENT
        IDKit.createSession(
            appID = testAppID,
            action = TEST_ACTION,
            verificationLevel = VerificationLevel.DOCUMENT
        )

        // Test SECURE_DOCUMENT
        IDKit.createSession(
            appID = testAppID,
            action = TEST_ACTION,
            verificationLevel = VerificationLevel.SECURE_DOCUMENT
        )

        coVerify(exactly = 4) {
            NetworkClient.createRequest(any(), any())
        }
    }

    // ========== createCredentialCategorySession Tests ==========

    @Test
    fun `createCredentialCategorySession returns Session with valid parameters`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(UUID.randomUUID())

        val session = IDKit.createCredentialCategorySession(
            appID = testAppID,
            action = TEST_ACTION,
            credentialCategory = testCredentialCategory,
            bridgeURL = BridgeURL.default,
            signal = TEST_SIGNAL,
            actionDescription = TEST_ACTION_DESCRIPTION
        )

        assertNotNull(session)
        assertNotNull(session.connectUrl)
    }

    @Test
    fun `createCredentialCategorySession throws on empty credentialCategory`() = runTest {
        val exception = assertThrows<AppErrorThrowable> {
            IDKit.createCredentialCategorySession(
                appID = testAppID,
                action = TEST_ACTION,
                credentialCategory = emptySet()
            )
        }

        assertTrue(exception.appError is AppError.InvalidInput)
        assertTrue(exception.message!!.contains("credentialCategory must not be empty"))
    }

    @Test
    fun `createCredentialCategorySession works with multiple categories`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(UUID.randomUUID())

        val session = IDKit.createCredentialCategorySession(
            appID = testAppID,
            action = TEST_ACTION,
            credentialCategory = setOf(
                CredentialCategory.PERSONHOOD,
                CredentialCategory.SECURE_DOCUMENT,
                CredentialCategory.DOCUMENT
            )
        )

        assertNotNull(session)
    }

    @Test
    fun `createCredentialCategorySession throws AppErrorThrowable on network failure`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } throws IOException("Network error")

        val exception = assertThrows<AppErrorThrowable> {
            IDKit.createCredentialCategorySession(
                appID = testAppID,
                action = TEST_ACTION,
                credentialCategory = testCredentialCategory
            )
        }

        assertTrue(exception.appError is AppError.GenericError)
    }

    @Test
    fun `createCredentialCategorySession uses default bridgeURL`() = runTest {
        coEvery {
            NetworkClient.createRequest(any(), any())
        } returns CreateRequestResponse(UUID.randomUUID())

        // Only required parameters
        val session = IDKit.createCredentialCategorySession(
            appID = testAppID,
            action = TEST_ACTION,
            credentialCategory = testCredentialCategory
        )

        assertNotNull(session)
        coVerify {
            NetworkClient.createRequest(any(), BridgeURL.default)
        }
    }
}
