package com.example

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Stable, privacy-preserving device identifier.
 * SHA-256(ANDROID_ID + salt) — consistent across app reinstalls on the same device,
 * not reversible back to the raw ANDROID_ID.
 */
object DeviceIdentity {

    private const val SALT = "KapasKiSehat2026_SecureSalt"

    fun computeId(context: Context): String {
        val rawId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest((rawId + SALT).toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
