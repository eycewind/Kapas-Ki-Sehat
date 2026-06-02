package com.example.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.CottonAceApplication
import com.example.DeviceIdentity
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

// ── Supabase payload shapes (CONTRACTS.md §1.1 / §1.2) ──────────────────────

// Canonical shape per CONTRACTS.md §1.1.
// Nullable fields are present in the schema; populated with real values since Phase 3.
@Serializable
data class DiagnosticLogPayload(
    val device_id: String,
    val district: String,
    val whitefly_count: Int,
    val risk_level: String,
    val confidence_score: Float,            // real ScanResponse.confidence, 0.0–1.0
    val timestamp: String,                  // ISO-8601 UTC
    val inference_time_ms: Int,             // measured round-trip (includes network)
    val image_storage_path: String? = null, // bare object key in leaf-images bucket
    val latitude: Double? = null,           // null when GPS unavailable — never 0.0
    val longitude: Double? = null,
    val agricultural_belt: String? = null   // TODO: derive from district (future pass)
)

@Serializable
data class ProfilePayload(
    val device_id: String,
    val app_version: String,
    val preferred_language: String
)

// ── Worker ───────────────────────────────────────────────────────────────────

class DataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CottonAceApplication
        val supabase = app.supabaseClient
        val database = app.database

        Log.d("CottonAceSync", "--> WorkManager sync triggered. Scanning local DB for unsynced logs...")

        return try {
            val pendingScans = database.scanHistoryDao().getPendingSyncScans().first()

            if (pendingScans.isEmpty()) {
                Log.d("CottonAceSync", "Nothing to sync.")
                return Result.success()
            }

            // Device ID: extracted to DeviceIdentity so scanner and worker share the same value.
            val deviceId = DeviceIdentity.computeId(applicationContext)

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }

            val profilePayload = ProfilePayload(
                device_id = deviceId,
                app_version = com.example.BuildConfig.VERSION_NAME, // gradle versionName (CONTRACTS.md §1.2)
                preferred_language = "ur"                           // canonical code (CONTRACTS.md §5)
            )

            val allPayloadsList = pendingScans.map { scan ->
                DiagnosticLogPayload(
                    device_id = deviceId,
                    district = scan.district,
                    whitefly_count = scan.whiteflyCount,
                    risk_level = scan.riskLevel,
                    confidence_score = scan.confidenceScore,        // real value (Phase 3)
                    timestamp = sdf.format(java.util.Date(scan.timestamp)),
                    inference_time_ms = scan.inferenceTimeMs.toInt(), // real value (Phase 3)
                    image_storage_path = scan.imageStoragePath,     // real value (Phase 3)
                    latitude = scan.latitude,                       // null when no GPS fix
                    longitude = scan.longitude
                )
            }

            Log.d("CottonAceSync", "Pushing ${allPayloadsList.size} record(s) to Supabase...")

            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    supabase.from("farmers_profiles").upsert(profilePayload)
                } catch (e: Exception) {
                    // Non-fatal: log at WARN — a persistent failure here is visible
                    // without blocking the diagnostic_logs sync. (CONTRACTS.md §10 #15)
                    Log.w("CottonAceSync", "farmers_profiles upsert failed (continuing): ${e.message}", e)
                }

                // insert() throws on any failure (HTTP error, RLS, network).
                // updateSyncStatus is only reached on confirmed success. (CONTRACTS.md §10 #14)
                supabase.from("diagnostic_logs").insert(allPayloadsList)
                Log.d("CottonAceSync", "INSERT confirmed for ${allPayloadsList.size} record(s). Marking local rows synced...")

                pendingScans.forEach { scan ->
                    database.scanHistoryDao().updateSyncStatus(scan.id, 1)
                }
                Log.d("CottonAceSync", "Sync complete — ${pendingScans.size} row(s) marked syncState=1.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("CottonAceSync", "Sync failed, will retry: ", e)
            Result.retry()
        }
    }
}
