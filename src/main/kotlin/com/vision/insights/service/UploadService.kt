package com.vision.insights.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.bson.json.JsonObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException

@Service
class UploadService(private val okHttpClient: OkHttpClient) {

    companion object {
        private const val BASE_URL = "https://api.pinata.cloud/v3"
        private const val UPLOADS_URL = "https://uploads.pinata.cloud/v3"
        private const val GATEWAY_URL = "https://lavender-efficient-wren-990.mypinata.cloud/ipfs/"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    @Value("\${ipfs.token}")
    private lateinit var authToken:String

    /**
     * Creates a new public group
     * @param name Name of the group
     * @return Response from Pinata API
     */
    fun createGroup(name: String): String  = runBlocking{
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

        val response = async { okHttpClient.newCall(request).execute() }
        val result = response.await().body?.string().also { println(it) }
        return@runBlocking result?.let { getGroupId(it) }!!

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
    ): String = runBlocking {
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

        val response = async { okHttpClient.newCall(request).execute() }
        val result = response.await().body?.string().also { println(it) }
        val cid = result?.let { getCid(it) }
        return@runBlocking "$GATEWAY_URL$cid"
    }

    /**
     * Lists all files in the public group
     * @return Response from Pinata API
     */
//    fun listFiles(groupId: String): String {
//        val request = Request.Builder()
//            .url("$BASE_URL/files/public?group=$groupId")
//            .get()
//            .addHeader("Authorization", "Bearer $authToken")
//            .build()
//
//        return executeRequest(request)
//    }

    /**
     * Gets file details by ID
     * @param id File ID
     * @return Response from Pinata API
     */
//    fun getFileById(id: String): String {
//        val request = Request.Builder()
//            .url("$BASE_URL/files/public/$id")
//            .get()
//            .addHeader("Authorization", "Bearer $authToken")
//            .build()
//
//        return executeRequest(request)
//    }

//    /**
//     * Deletes a file by ID
//     * @param id File ID
//     * @return Response from Pinata API
//     */
//    fun deleteFile(id: String): String {
//        val request = Request.Builder()
//            .url("$BASE_URL/files/public/$id")
//            .delete()
//            .addHeader("Authorization", "Bearer $authToken")
//            .build()
//
//        return executeRequest(request)
//    }


    private fun getCid(response: String): String? {
        // Remove leading '"' and trailing '"' (plus any newlines)
        val trimmed = response.trim().removePrefix("\"").removeSuffix("\"")
        // Un-escape the JSON in one go
        val unescaped = trimmed.replace("\\\"", "\"")
        val objectMapper = ObjectMapper()
        val rootNode = objectMapper.readTree(unescaped)
        return rootNode.path("data").path("cid").asText(null)
    }

    private fun getGroupId(response: String): String? {
        // Remove leading '"' and trailing '"' (plus any newlines)
        val trimmed = response.trim().removePrefix("\"").removeSuffix("\"")
        // Un-escape the JSON in one go
        val unescaped = trimmed.replace("\\\"", "\"")
        val mapper = ObjectMapper()
        val root = mapper.readTree(unescaped)
        return root.path("data").path("id").asText(null)
    }
}