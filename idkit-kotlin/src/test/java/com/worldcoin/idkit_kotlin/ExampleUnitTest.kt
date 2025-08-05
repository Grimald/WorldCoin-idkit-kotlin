package com.worldcoin.idkit_kotlin
import kotlinx.coroutines.runBlocking

import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class IDKitTest {

    private val appID = AppID("app_ce4cb73cb75fc3b73b71ffb4de178410")
    private val action = "test-action"

    @Test
    fun testVerificationSession() = runBlocking {
        try {
            // Create a Session
            val session = Session.create(
                appID = appID,
                action = action
            )

            // Generate the connect URL (you would typically display this as a QR code)
            val connectUrl = session.connectUrl
            println("Scan this URL with the World App: $connectUrl")

            // Monitor the session status
            session.status().collect { status ->
                when (status) {
                    is Status.WaitingForConnection -> {
                        println("Waiting for the user to scan the QR Code")
                    }
                    is Status.AwaitingConfirmation -> {
                        println("Awaiting user confirmation")
                    }
                    is Status.Confirmed -> {
                        println("Got proof: ${status.proof}")
                    }
                    is Status.Failed -> {
                        println("Got error: ${status.error.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("An error occurred: ${e.localizedMessage}")
        }
    }

    @Test
    fun testCredentialCategorySession() = runBlocking {
        try {
            // Create a Session
            val session = Session.createCredentialCategorySession(
                appID = appID,
                action = action,
                credentialCategory = setOf(CredentialCategory.DOCUMENT)
            )

            // Generate the connect URL (you would typically use it to launch the World App and start verifying credential)
            val connectUrl = session.connectUrl
            println("Use this URL to launch the World App: $connectUrl")

            // Monitor the session status
            session.status().collect { status ->
                when (status) {
                    is Status.WaitingForConnection -> {
                        println("Waiting for the user to launch the World App")
                    }
                    is Status.AwaitingConfirmation -> {
                        println("Awaiting for user confirmation of credential")
                    }
                    is Status.Confirmed -> {
                        println("Got proof: ${status.proof}")
                    }
                    is Status.Failed -> {
                        println("Got error: ${status.error.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("An error occurred: ${e.localizedMessage}")
        }
    }
}
