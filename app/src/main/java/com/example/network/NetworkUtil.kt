package com.example.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ScanResponse(
    val pest_type: String,
    val confidence: Float,
    val recommendation_ur: String
)

object ApiClient {
    private const val BASE_URL = "http://192.168.18.11:8000"

    val client = HttpClient(Android) {
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

    suspend fun uploadScan(imageFile: File): ScanResponse {
        val response = client.post("$BASE_URL/api/v1/scan") {
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", imageFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"${imageFile.name}\"")
                    })
                }
            ))
        }
        return response.body()
    }
}
