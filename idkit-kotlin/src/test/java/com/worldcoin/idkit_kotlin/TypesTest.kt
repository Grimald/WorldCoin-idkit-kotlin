package com.worldcoin.idkit_kotlin

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypesTest {

    @Test
    fun `AppID requires app_ prefix`() {
        val appId = AppID("app_123456")
        assertEquals("app_123456", appId.rawId)
    }

    @Test
    fun `AppID throws on invalid prefix`() {
        assertFailsWith<IllegalArgumentException> {
            AppID("invalid_123")
        }
    }

    @Test
    fun `AppID throws on empty string`() {
        assertFailsWith<IllegalArgumentException> {
            AppID("")
        }
    }

    @Test
    fun `AppID isStaging returns false for production app`() {
        val appId = AppID("app_123456")
        assertFalse(appId.isStaging)
    }

    @Test
    fun `AppID isStaging returns true for staging app`() {
        val appId = AppID("app_staging_123456")
        assertTrue(appId.isStaging)
    }

    @Test
    fun `BridgeURL accepts default production URL`() {
        val bridgeURL = BridgeURL.default
        assertEquals("https://bridge.worldcoin.org", bridgeURL.rawURL)
    }

    @Test
    fun `BridgeURL accepts https URL`() {
        val bridgeURL = BridgeURL("https://custom.bridge.com")
        assertEquals("https://custom.bridge.com", bridgeURL.rawURL)
    }

    @Test
    fun `BridgeURL accepts localhost without https`() {
        val bridgeURL = BridgeURL("http://localhost:8080")
        assertEquals("http://localhost:8080", bridgeURL.rawURL)
    }

    @Test
    fun `BridgeURL accepts 127_0_0_1 without https`() {
        val bridgeURL = BridgeURL("http://127.0.0.1:8080")
        assertEquals("http://127.0.0.1:8080", bridgeURL.rawURL)
    }

    @Test
    fun `BridgeURL throws on http non-localhost`() {
        assertFailsWith<IllegalArgumentException> {
            BridgeURL("http://insecure.com")
        }
    }

    @Test
    fun `BridgeURL throws on invalid URL format`() {
        assertFailsWith<IllegalArgumentException> {
            BridgeURL("not a url")
        }
    }

    @Test
    fun `AppErrorThrowable wraps AppError`() {
        val error = AppError.ConnectionFailed
        val throwable = AppErrorThrowable(error)

        assertEquals(error, throwable.appError)
        assertEquals(error.message, throwable.message)
    }
}
