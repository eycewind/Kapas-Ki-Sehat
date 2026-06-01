package com.example.network

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

@Serializable
data class ScanResponse(
    // Defensive defaults: the backend may omit fields (or return an error envelope).
    // Without defaults a missing required field throws MissingFieldException at parse time.
    val pest_type: String = "Unknown",
    val confidence: Float = 0f,
    val recommendation_ur: String = "",
    // Captured so we can detect the backend's error envelope: { "status": "error", ... }
    val status: String? = null
)

object ApiClient {
    private const val BASE_URL = "http://192.168.18.11:8000"

    val client = HttpClient(Android) {
        // Fail instead of hanging forever when the backend/ngrok tunnel is unreachable.
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun uploadScan(imageFile: File, lat: Double, lon: Double): ScanResponse {
        val response = client.post("$BASE_URL/api/v1/scan") {
            setBody(MultiPartFormDataContent(
                formData {
                    append("latitude", lat.toString())
                    append("longitude", lon.toString())
                    append("file", imageFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"${imageFile.name}\"")
                    })
                }
            ))
        }

        if (!response.status.isSuccess()) {
            throw IOException("Scan request failed: HTTP ${response.status.value} ${response.status.description}")
        }

        val body = response.body<ScanResponse>()
        if (body.status == "error") {
            throw IOException("Scan failed: backend returned an error status")
        }
        return body
    }
}
