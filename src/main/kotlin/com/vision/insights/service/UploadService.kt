package com.vision.insights.service

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException

@Service
class UploadService(private val okHttpClient: OkHttpClient) {

    companion object {
        private const val BASE_URL = "https://api.pinata.cloud/v3"
        private const val UPLOADS_URL = "https://uploads.pinata.cloud/v3"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    // Replace with your actual token
    private val authToken: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySW5mb3JtYXRpb24iOnsiaWQiOiJjYzFmM2I5Yi01YjgwLTQ1MzMtOGMyOS01ZWM5YzkxOGUwZGMiLCJlbWFpbCI6ImFrc2hheXRoMjAwNEBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicGluX3BvbGljeSI6eyJyZWdpb25zIjpbeyJkZXNpcmVkUmVwbGljYXRpb25Db3VudCI6MSwiaWQiOiJGUkExIn0seyJkZXNpcmVkUmVwbGljYXRpb25Db3VudCI6MSwiaWQiOiJOWUMxIn1dLCJ2ZXJzaW9uIjoxfSwibWZhX2VuYWJsZWQiOmZhbHNlLCJzdGF0dXMiOiJBQ1RJVkUifSwiYXV0aGVudGljYXRpb25UeXBlIjoic2NvcGVkS2V5Iiwic2NvcGVkS2V5S2V5IjoiOWQyMzUyMDczOTcxMmJmOWMwYmMiLCJzY29wZWRLZXlTZWNyZXQiOiJkMDhiOWZiM2YwNmI1M2QwY2RmMmNjZjc0MjRkNTk3OTAzNTFlOTU1ZTU4MGI2MGFhOWE5OTZlMGE4MDNmYmRiIiwiZXhwIjoxNzc4MTUwODk4fQ.ui2-yhvmpcw2FEDievPvdrF5pRzjRLhylvARA7EAuMg"


    /**
     * Creates a new public group
     * @param name Name of the group
     * @return Response from Pinata API
     */
    fun createGroup(name: String): String {
        val json = """
            {
                "name": "$name",
                "is_public": true
            }
        """.trimIndent()

        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/groups/public")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .build()

        return executeRequest(request)
    }

    /**
     * Uploads a file to Pinata
     * @param file File to upload
     * @param name Name for the file
     * @param groupId Group ID to associate with the file
     * @param network Network type (public by default)
     * @return Response from Pinata API
     */
    fun uploadFile(
        file: File,
        name: String,
        groupId: String,
        network: String = "public"
    ): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("network", network)
            .addFormDataPart("file", file.name, file.asRequestBody("multipart/form-data".toMediaTypeOrNull()))
            .addFormDataPart("name", name)
            .addFormDataPart("group_id", groupId)
            .build()

        val request = Request.Builder()
            .url("$UPLOADS_URL/files")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        return executeRequest(request)
    }

    /**
     * Lists all files in the public group
     * @return Response from Pinata API
     */
    fun listFiles(): String {
        val request = Request.Builder()
            .url("$BASE_URL/files/public")
            .get()
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        return executeRequest(request)
    }

    /**
     * Gets file details by ID
     * @param id File ID
     * @return Response from Pinata API
     */
    fun getFileById(id: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/files/public/$id")
            .get()
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        return executeRequest(request)
    }

    /**
     * Deletes a file by ID
     * @param id File ID
     * @return Response from Pinata API
     */
    fun deleteFile(id: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/files/public/$id")
            .delete()
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        return executeRequest(request)
    }

    private fun executeRequest(request: Request): String {
        return try {
            val response = okHttpClient.newCall(request).execute()
            response.body?.string() ?: throw IOException("Empty response body")
        } catch (e: Exception) {
            throw IOException("Failed to execute request: ${e.message}", e)
        }
    }
}