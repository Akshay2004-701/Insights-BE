package com.vision.insights.service.analysis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Service class for analyzing images using the Roboflow API with OkHttp client.
 */
@Service
class ImageAnalysisService{
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    @Value("\${model.apiKey}")
    private lateinit var apiKey:String

    @Value("\${model.apiUrl}")
    private lateinit var apiUrl:String

    private val objectMapper = ObjectMapper()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Analyzes an image by sending it to the Roboflow API.
     *
     * @param imageBytes The image data as a byte array
     * @return The API response as a map
     */
    fun analyzeImage(imageBytes: ByteArray): Map<String, Any> {
        // Convert image to Base64
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)

        // Prepare request body as JSON
        val requestBodyMap = mapOf(
            "api_key" to apiKey,
            "inputs" to mapOf(
                "image" to mapOf(
                    "type" to "base64",
                    "value" to base64Image
                )
            )
        )

        val requestBodyJson = objectMapper.writeValueAsString(requestBodyMap)
        val requestBody = requestBodyJson.toRequestBody(jsonMediaType)

        // Create the request
        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        // Execute the request
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("API call failed with code: ${response.code}, message: ${response.message}")
            }

            val responseBody = response.body?.string() ?: throw RuntimeException("Empty response body")
            return objectMapper.readValue(responseBody)
        }
    }
}