package com.example.network

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.CottonAceApplication
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticLogPayload(
    val device_id: String,
    val district: String,
    val whitefly_count: Int,
    val risk_level: String,
    val confidence_score: Float,
    val timestamp: String,
    val inference_time_ms: Int
)

@Serializable
data class ProfilePayload(
    val device_id: String,
    val app_version: String,
    val preferred_language: String
)

class DataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CottonAceApplication
        val supabase = app.supabaseClient
        val database = app.database

        Log.d("CottonAceSync", "--> WorkManager Sync task triggered! Scanning local DB for unsynced logs...")

        return try {
            val pendingScans = database.scanHistoryDao().getPendingSyncScans().first()

            if (pendingScans.isEmpty()) {
                return Result.success()
            }

            val rawDeviceId = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"

            val salt = "KapasKiSehat2026_SecureSalt"
            val combinedId = rawDeviceId + salt
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(combinedId.toByteArray(Charsets.UTF_8))
            val deviceId = hashBytes.joinToString("") { "%02x".format(it) }

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }

            val profilePayload = ProfilePayload(
                device_id = deviceId,
                app_version = com.example.BuildConfig.VERSION_NAME, // gradle versionName, per CONTRACTS.md §1
                preferred_language = "ur" // canonical language code, not "URDU" (CONTRACTS.md §5)
            )

            val allPayloadsList = pendingScans.map { scan ->
                val isoTimestamp = sdf.format(java.util.Date(scan.timestamp))
                DiagnosticLogPayload(
                    device_id = deviceId,
                    district = scan.district,
                    whitefly_count = scan.whiteflyCount,
                    risk_level = scan.riskLevel,
                    confidence_score = 0.95f,
                    timestamp = isoTimestamp,
                    inference_time_ms = 150
                )
            }

            Log.d("CottonAceSync", "Found unsynced records. Attempting batched network push to Supabase table...")

            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    supabase.from("farmers_profiles").upsert(profilePayload)
                } catch (e: Exception) {
                    // Non-fatal: a failed profile upsert must not block diagnostic_logs sync,
                    // but log at WARN so a persistent failure is visible (CONTRACTS.md §10 #15).
                    Log.w("CottonAceSync", "farmers_profiles upsert failed (continuing): ${e.message}", e)
                }

                // Batch upload payloads to Supabase
                supabase.from("diagnostic_logs").insert(allPayloadsList)
                
                // Mark entities as synced (syncState = 1) locally
                pendingScans.forEach { scan ->
                    database.scanHistoryDao().updateSyncStatus(scan.id, 1)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("CottonAceSync", "NETWORK ERROR ENCOUNTERED: ", e)
            Result.retry()
        }
    }
}
