package com.worldcoin.idkit_kotlin.impl

import android.util.Log
import com.worldcoin.idkit_kotlin.BuildConfig

/**
 * Logging utilities for the IDKit SDK.
 * All logs are only enabled in debug builds.
 */

private const val SDK_TAG = "IdKit-Kotlin"

internal fun logDebug(tag: String = SDK_TAG, message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(tag, message)
    }
}

internal fun logWarning(tag: String = SDK_TAG, message: String) {
    if (BuildConfig.DEBUG) {
        Log.w(tag, message)
    }
}

internal fun logError(tag: String = SDK_TAG, message: String, throwable: Throwable? = null) {
    if (BuildConfig.DEBUG) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
