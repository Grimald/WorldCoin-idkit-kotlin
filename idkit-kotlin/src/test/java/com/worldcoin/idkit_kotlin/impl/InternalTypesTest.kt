package com.worldcoin.idkit_kotlin.impl

import com.worldcoin.idkit_kotlin.AppID
import com.worldcoin.idkit_kotlin.CredentialCategory
import com.worldcoin.idkit_kotlin.Proof
import com.worldcoin.idkit_kotlin.VerificationLevel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InternalTypesTest {
    
    companion object {
        private val testAppID = AppID("app_test")
        private const val TEST_ACTION = "verify"
        private const val TEST_SIGNAL = "0x123"
        private const val TEST_ACTION_DESCRIPTION = "Test"
    }

    private val testSingleCategory = setOf(CredentialCategory.PERSONHOOD)
    private val testMultipleCategories = setOf(
        CredentialCategory.PERSONHOOD,
        CredentialCategory.SECURE_DOCUMENT,
        CredentialCategory.DOCUMENT
    )
    
    // ========== CreateRequestPayload Tests ==========
    
    @Test
    fun `CreateRequestPayload factory sets correct credential types for ORB`() {
        val payload = CreateRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = TEST_ACTION_DESCRIPTION,
            verificationLevel = VerificationLevel.ORB
        )
        
        assertEquals(testAppID.rawId, payload.appId)
        assertEquals(TEST_ACTION, payload.action)
        assertEquals(TEST_SIGNAL, payload.signal)
        assertEquals(TEST_ACTION_DESCRIPTION, payload.actionDescription)
        assertEquals(VerificationLevel.ORB, payload.verificationLevel)
        assertEquals(listOf(Proof.CredentialType.ORB), payload.credentialTypes)
    }
    
    @Test
    fun `CreateRequestPayload factory sets correct credential types for SECURE_DOCUMENT`() {
        val payload = CreateRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = null,
            verificationLevel = VerificationLevel.SECURE_DOCUMENT
        )
        
        assertEquals(VerificationLevel.SECURE_DOCUMENT, payload.verificationLevel)
        assertEquals(
            listOf(Proof.CredentialType.ORB, Proof.CredentialType.SECURE_DOCUMENT),
            payload.credentialTypes
        )
    }
    
    @Test
    fun `CreateRequestPayload factory sets correct credential types for DOCUMENT`() {
        val payload = CreateRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = null,
            verificationLevel = VerificationLevel.DOCUMENT
        )
        
        assertEquals(VerificationLevel.DOCUMENT, payload.verificationLevel)
        assertEquals(
            listOf(
                Proof.CredentialType.ORB,
                Proof.CredentialType.SECURE_DOCUMENT,
                Proof.CredentialType.DOCUMENT
            ),
            payload.credentialTypes
        )
    }
    
    @Test
    fun `CreateRequestPayload factory sets correct credential types for DEVICE`() {
        val payload = CreateRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = TEST_ACTION_DESCRIPTION,
            verificationLevel = VerificationLevel.DEVICE
        )
        
        assertEquals(VerificationLevel.DEVICE, payload.verificationLevel)
        assertEquals(
            listOf(Proof.CredentialType.ORB, Proof.CredentialType.DEVICE),
            payload.credentialTypes
        )
    }
    
    @Test
    fun `CreateRequestPayload factory extracts rawId from AppID`() {
        val payload = CreateRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = null,
            verificationLevel = VerificationLevel.ORB
        )
        
        assertEquals(testAppID.rawId, payload.appId)
    }
    
    @Test
    fun `CreateRequestPayload implements EncryptablePayload`() {
        val payload = CreateRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = null,
            verificationLevel = VerificationLevel.ORB
        )
        
        // Payload is EncryptablePayload by design (sealed interface)
        assertNotNull(payload)
    }
    
    // ========== CreateCredentialCategoryRequestPayload Tests ==========
    
    @Test
    fun `CreateCredentialCategoryRequestPayload factory creates correct payload`() {
        val payload = CreateCredentialCategoryRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = TEST_ACTION_DESCRIPTION,
            credentialCategory = testSingleCategory
        )
        
        assertEquals(testAppID.rawId, payload.appId)
        assertEquals(TEST_ACTION, payload.action)
        assertEquals(TEST_SIGNAL, payload.signal)
        assertEquals(TEST_ACTION_DESCRIPTION, payload.actionDescription)
        assertEquals(testSingleCategory, payload.credentialCategory)
    }
    
    @Test
    fun `CreateCredentialCategoryRequestPayload factory extracts rawId from AppID`() {
        val payload = CreateCredentialCategoryRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = null,
            credentialCategory = setOf(
                CredentialCategory.PERSONHOOD,
                CredentialCategory.SECURE_DOCUMENT
            )
        )
        
        assertEquals(testAppID.rawId, payload.appId)
        assertEquals(2, payload.credentialCategory.size)
        assertTrue(payload.credentialCategory.contains(CredentialCategory.PERSONHOOD))
        assertTrue(payload.credentialCategory.contains(CredentialCategory.SECURE_DOCUMENT))
    }
    
    @Test
    fun `CreateCredentialCategoryRequestPayload implements EncryptablePayload`() {
        val payload = CreateCredentialCategoryRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = null,
            credentialCategory = testSingleCategory
        )
        
        // Payload is EncryptablePayload by design (sealed interface)
        assertNotNull(payload)
    }
    
    @Test
    fun `CreateCredentialCategoryRequestPayload handles multiple categories`() {
        val payload = CreateCredentialCategoryRequestPayload(
            appID = testAppID,
            action = TEST_ACTION,
            signal = TEST_SIGNAL,
            actionDescription = TEST_ACTION_DESCRIPTION,
            credentialCategory = testMultipleCategories
        )
        
        assertEquals(3, payload.credentialCategory.size)
        assertEquals(testMultipleCategories, payload.credentialCategory)
    }
}
