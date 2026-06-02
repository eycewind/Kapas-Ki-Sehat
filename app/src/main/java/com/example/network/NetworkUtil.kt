package com.example.network

import com.example.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

// ── ScanResponse ─────────────────────────────────────────────────────────────
// Canonical shape matches CONTRACTS.md §3.1.
// All fields have safe defaults so a missing field never crashes deserialization
// (ignoreUnknownKeys tolerates extra fields; defaults handle missing ones).
@Serializable
data class ScanResponse(
    val status: String? = null,                 // "success" | "error"
    val pest_type: String = "Unknown",
    val confidence: Float = 0f,                 // 0.0–1.0
    val confidence_score: Float = 0f,           // duplicate of confidence (backend sends both)
    val whitefly_count: Int = 0,
    val recommendation_ur: String = "",
    val recommendation_en: String = ""
)

// ── ApiClient ─────────────────────────────────────────────────────────────────
object ApiClient {

    // Read from BuildConfig, which the Secrets Gradle Plugin populates from
    // .env (gitignored) with .env.example as the committed fallback.
    // To rotate ngrok: update BACKEND_BASE_URL in .env → Gradle sync → done.
    // No Kotlin source change or full recompile needed.
    private val BASE_URL get() = BuildConfig.BACKEND_BASE_URL

    val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000   // total request time
            connectTimeoutMillis = 15_000   // initial connection
            socketTimeoutMillis  = 30_000   // per-read idle
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true    // backend may add fields; don't crash
                prettyPrint = true
                isLenient = true
            })
        }
    }

    // lat / lon are nullable — pass null when GPS is unavailable.
    // The backend omits the field from the form entirely; 0.0 must not be sent
    // as it is a real coordinate (Gulf of Guinea). CONTRACTS.md §3.1.
    suspend fun uploadScan(imageFile: File, lat: Double?, lon: Double?): ScanResponse {
        val response = client.post("$BASE_URL/api/v1/scan") {
            setBody(MultiPartFormDataContent(
                formData {
                    if (lat != null) append("latitude", lat.toString())
                    if (lon != null) append("longitude", lon.toString())
                    append("file", imageFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"${imageFile.name}\"")
                    })
                }
            ))
        }

        if (!response.status.isSuccess()) {
            throw IOException(
                "Scan request failed: HTTP ${response.status.value} ${response.status.description}"
            )
        }

        val body = response.body<ScanResponse>()
        if (body.status == "error") {
            throw IOException("Scan failed: backend returned status=error")
        }
        return body
    }
}
