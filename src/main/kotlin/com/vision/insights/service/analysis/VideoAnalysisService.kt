package com.vision.insights.service.analysis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Service class that combines video processing, frame analysis, and summary generation.
 * Takes a video URL, processes it frame by frame using coroutines for concurrency,
 * and generates a unified analysis report.
 */
@Service
class VideoAnalysisService(
    private val videoProcessingService: VideoProcessingService,
    private val imageAnalysisService: ImageAnalysisService
) {
    private val logger = LoggerFactory.getLogger(VideoAnalysisService::class.java)
    private val objectMapper = ObjectMapper()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Value("\${gemini.apiKey}")
    private lateinit var geminiApiKey:String

    @Value("\${gemini.apiUrl}")
    private lateinit var geminiApiUrl:String

    // Configure OkHttpClient factory with appropriate timeouts
    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Maximum number of concurrent image analysis tasks
    private val MAX_CONCURRENT_ANALYSES = 2

    // Maximum retries for failed API calls
    private val MAX_RETRIES = 3

    // Delay between retries (in milliseconds)
    private val RETRY_DELAY_MS = 2000L

    // Delay between batches (in milliseconds)
    private val BATCH_DELAY_MS = 1000L

    /**
     * Processes a video from a URL, analyzes each frame with coroutines, and generates a unified report.
     * This is a suspending function that should be called from a coroutine context.
     *
     * @param videoUrl URL of the video to analyze
     * @return A comprehensive analysis report for the video
     */
    suspend fun analyzeVideoAsync(videoUrl: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        logger.info("Starting video analysis for URL: $videoUrl")

        try {
            // Step 1: Extract frames from video
            logger.info("Extracting frames from video...")
            val frames = videoProcessingService.extractFramesAtOneSecondInterval(videoUrl)
            logger.info("Successfully extracted ${frames.size} frames")

            // Step 2: Analyze each frame using coroutines
            logger.info("Analyzing frames with coroutines...")
            val frameAnalyses = analyzeFramesAsync(frames)
            logger.info("Successfully analyzed all frames")

            // Step 3: Generate summary report using Gemini API
            logger.info("Generating summary report...")
            val summaryReport = generateSummaryReportAsync(frameAnalyses)
            logger.info("Successfully generated summary report")

            // Create the final response
            mapOf(
                "success" to true,
                "videoUrl" to videoUrl,
                "totalFrames" to frames.size,
                "frameAnalyses" to frameAnalyses,
                "summaryReport" to summaryReport
            )
        } catch (e: Exception) {
            logger.error("Error analyzing video", e)
            mapOf(
                "success" to false,
                "error" to e.message,
                "videoUrl" to videoUrl
            )
        }
    }

    /**
     * For backward compatibility - non-suspending function that delegates to the suspending version.
     * Use this from non-coroutine contexts.
     *
     * @param videoUrl URL of the video to analyze
     * @return A comprehensive analysis report for the video
     */
    fun analyzeVideo(videoUrl: String): Map<String, Any?> {
        return runBlocking {
            analyzeVideoAsync(videoUrl)
        }
    }

    /**
     * Analyzes a list of video frames concurrently using coroutines.
     * Processes frames in batches to avoid overwhelming the system,
     * with retry logic for failed requests.
     *
     * @param frames Array of frame images as ByteArrays
     * @return List of analysis results for each frame
     */
    private suspend fun analyzeFramesAsync(frames: Array<ByteArray>): List<Map<String, Any>> = coroutineScope {
        // Process frames sequentially for stability
        val results = mutableListOf<Map<String, Any>>()

        // Process frames in small batches to control concurrency
        val batchSize = min(MAX_CONCURRENT_ANALYSES, 2) // Keep batches very small

        frames.toList()
            .chunked(batchSize)
            .forEachIndexed { batchIndex, batch ->
                // Add delay between batches to avoid overloading the API
                if (batchIndex > 0) {
                    delay(BATCH_DELAY_MS)
                }

                logger.info("Processing batch ${batchIndex + 1}/${frames.size / batchSize + 1} with ${batch.size} frames")

                // Process each batch concurrently
                val batchResults = batch.mapIndexed { inBatchIndex, frameData ->
                    val frameIndex = batchIndex * batchSize + inBatchIndex

                    async {
                        // Retry logic for more stability
                        analyzeFrameWithRetry(frameData, frameIndex)
                    }
                }.awaitAll()

                results.addAll(batchResults)
                logger.info("Completed batch ${batchIndex + 1}, total frames processed: ${results.size}/${frames.size}")
            }

        results
    }

    /**
     * Analyzes a single frame with retry logic for resilience.
     *
     * @param frameData The image data as a byte array
     * @param frameIndex The index of the frame in the sequence
     * @return Analysis result for the frame
     */
    private suspend fun analyzeFrameWithRetry(frameData: ByteArray, frameIndex: Int): Map<String, Any> {
        var attempts = 0
        var lastException: Exception? = null

        // Retry loop
        while (attempts < MAX_RETRIES) {
            attempts++

            try {
                val startTime = System.currentTimeMillis()

                // Use withContext to ensure IO operations run on the IO dispatcher
                val frameAnalysis = withContext(Dispatchers.IO) {
                    logger.debug("Analyzing frame $frameIndex (attempt $attempts)")
                    imageAnalysisService.analyzeImage(frameData)
                }

                val duration = System.currentTimeMillis() - startTime
                logger.debug("Successfully analyzed frame $frameIndex in $duration ms (attempt $attempts)")

                // Add frame index to the analysis result
                val resultWithIndex = frameAnalysis.toMutableMap()
                resultWithIndex["frameIndex"] = frameIndex
                resultWithIndex["frameTimeSeconds"] = frameIndex // Assuming 1 frame per second

                return resultWithIndex
            } catch (e: Exception) {
                lastException = e
                logger.warn("Error analyzing frame $frameIndex (attempt $attempts): ${e.message}")

                // Wait before retrying
                if (attempts < MAX_RETRIES) {
                    val delayTime = RETRY_DELAY_MS * attempts // Exponential backoff
                    logger.info("Retrying frame $frameIndex in $delayTime ms...")
                    delay(delayTime)
                }
            }
        }

        // If all retries failed, return an error result
        logger.error("All $MAX_RETRIES attempts failed for frame $frameIndex", lastException)
        return mapOf(
            "frameIndex" to frameIndex,
            "frameTimeSeconds" to frameIndex,
            "error" to "Failed to analyze frame after $MAX_RETRIES attempts: ${lastException?.message}"
        )
    }

    /**
     * Generates a summary report based on individual frame analyses using the Gemini API.
     * This is a suspending function that executes in the IO context.
     *
     * @param frameAnalyses List of analysis results for each frame
     * @return Summary report generated by Gemini API
     */
    private suspend fun generateSummaryReportAsync(frameAnalyses: List<Map<String, Any>>): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            // If frameAnalyses is empty, return a basic message
            if (frameAnalyses.isEmpty()) {
                return@withContext mapOf(
                    "summary" to "No frames were available for analysis",
                    "timestamp" to System.currentTimeMillis()
                )
            }

            // Extract insights from frame analyses
            val insights = extractInsightsFromAnalyses(frameAnalyses)

            // If we have the Gemini API key, use it to generate a summary
            if (geminiApiKey.isNotBlank()) {
                callGeminiApi(insights)
            } else {
                // Otherwise, return the simplified insights directly
                mapOf(
                    "summary" to insights.joinToString("\n"),
                    "timestamp" to System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            logger.error("Error generating summary report", e)
            mapOf(
                "error" to "Failed to generate summary: ${e.message}",
                "timestamp" to System.currentTimeMillis()
            )
        }
    }

    /**
     * Extracts insights from the frame analyses in a format suitable for summarization.
     * Updated to handle structured JSON output containing detailed analysis fields.
     *
     * @param frameAnalyses List of analysis results for each frame
     * @return List of extracted insights
     */
    private fun extractInsightsFromAnalyses(frameAnalyses: List<Map<String, Any>>): List<String> {
        val allInsights = mutableListOf<String>()

        frameAnalyses.forEach { analysis ->
            try {
                val frameIndex = analysis["frameIndex"] ?: "unknown"
                val frameTime = analysis["frameTimeSeconds"] ?: frameIndex

                // Find insights in the response based on the new format
                val outputs = analysis["outputs"] as? List<*> ?: emptyList<Any>()
                outputs.forEach { output ->
                    if (output is Map<*, *>) {
                        val geminiOutput = output["google_gemini"] as? Map<*, *>
                        val outputText = geminiOutput?.get("output") as? String

                        if (outputText != null) {
                            // Extract JSON from Markdown code block
                            val jsonPattern = "```json\\s*(\\{.*?})\\s*```".toRegex(RegexOption.DOT_MATCHES_ALL)
                            val matchResult = jsonPattern.find(outputText)

                            if (matchResult != null) {
                                val jsonContent = matchResult.groupValues[1]
                                try {
                                    val jsonMap = objectMapper.readValue<Map<String, Any>>(jsonContent)

                                    // Extract structured data from the new JSON format

                                    // Overall description
                                    val overallDescription = jsonMap["overall_description"] as? String
                                    if (overallDescription != null) {
                                        allInsights.add("At ${frameTime}s: $overallDescription")
                                    }

                                    // Place information
                                    val placeInfo = jsonMap["place_information"] as? Map<*, *>
                                    if (placeInfo != null) {
                                        val locationType = placeInfo["location_type"] as? String
                                        val envContext = placeInfo["environmental_context"] as? String
                                        val features = placeInfo["notable_features"] as? List<*>

                                        if (locationType != null && envContext != null) {
                                            allInsights.add("At ${frameTime}s: Setting is a $locationType ($envContext)")
                                        }

                                        if (features != null && features.isNotEmpty()) {
                                            allInsights.add("At ${frameTime}s: Notable features: ${features.joinToString(", ")}")
                                        }
                                    }

                                    // People analysis
                                    val peopleAnalysis = jsonMap["people_analysis"] as? List<*>
                                    peopleAnalysis?.firstOrNull()?.let { peopleData ->
                                        if (peopleData is Map<*, *>) {
                                            val count = peopleData["number_of_individuals"]
                                            val activity = (jsonMap["behavior_analysis"] as? List<*>)?.firstOrNull()?.let {
                                                (it as? Map<*, *>)?.get("dominant_activity")
                                            }

                                            if (count != null && activity != null) {
                                                allInsights.add("At ${frameTime}s: $count people $activity")
                                            }
                                        }
                                    }

                                    // Movement analysis
                                    val movementAnalysis = jsonMap["movement_analysis"] as? Map<*, *>
                                    if (movementAnalysis != null && movementAnalysis["velocity_estimate"] != "stationary") {
                                        val direction = movementAnalysis["flow_direction"]
                                        val velocity = movementAnalysis["velocity_estimate"]
                                        allInsights.add("At ${frameTime}s: Movement detected - $direction direction at $velocity")
                                    }

                                    // Legacy support - check for insights array if present
                                    val insightsList = jsonMap["insights"] as? List<*>
                                    if (insightsList != null && insightsList.isNotEmpty()) {
                                        allInsights.addAll(insightsList.map { "At ${frameTime}s: ${it.toString()}" })
                                    }

                                } catch (e: Exception) {
                                    logger.warn("Error parsing JSON from output: $e")
                                    // Add raw text if JSON parsing fails
                                    allInsights.add("At ${frameTime}s: $outputText")
                                }
                            } else {
                                // Add raw text if no JSON pattern found
                                allInsights.add("At ${frameTime}s: $outputText")
                            }
                        }

                        // Extract any class information
                        val classes = geminiOutput?.get("classes") as? List<*>
                        if (classes != null && classes.isNotEmpty()) {
                            allInsights.add("At ${frameTime}s: Detected objects: ${classes.joinToString(", ")}")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Error extracting insights from frame ${analysis["frameIndex"]}: ${e.message}")
            }
        }

        return allInsights
    }

    /**
     * Calls the Gemini API to generate a summary based on insights.
     * Uses a separate OkHttpClient instance for this request.
     *
     * @param insights List of extracted insights from frame analyses
     * @return Summary report from Gemini API
     */
    private fun callGeminiApi(insights: List<String>): Map<String, Any> {
        // Create a new client for this request
        val client = createHttpClient()

        // Build a more focused prompt for the Gemini API
        val prompt = """
            Below are insights extracted from analyzing frames of a video (1 frame per second).
            Please provide a comprehensive summary of the video content based on these insights.
            
            FRAME INSIGHTS:
            ${insights.joinToString("\n")}
            
            Please provide a summary that includes:
            1. Overall video content description
            2. Key objects and people visible
            3. Notable scene changes or events
            4. Any patterns observed across frames
            
            Format your summary as descriptive paragraphs that tell the story of what happens in the video.
        """.trimIndent()

        val requestBodyMap = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf(
                            "text" to prompt
                        )
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.2,
                "topK" to 40,
                "topP" to 0.95,
                "maxOutputTokens" to 4096
            )
        )

        val requestBodyJson = objectMapper.writeValueAsString(requestBodyMap)
        val requestBody = requestBodyJson.toRequestBody(jsonMediaType)

        // Create the request
        val request = Request.Builder()
            .url("$geminiApiUrl?key=$geminiApiKey")
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        // Execute the request
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.error("Gemini API call failed: ${response.code} - ${response.message}")
                    return mapOf(
                        "error" to "Failed to generate summary: ${response.message}",
                        "status" to response.code
                    )
                }

                val responseBody = response.body?.string() ?: throw IOException("Empty response from Gemini API")
                val responseMap = objectMapper.readValue(responseBody, Map::class.java) as Map<*, *>

                // Extract the text content from Gemini response
                try {
                    val candidatesList = responseMap["candidates"] as List<*>
                    val candidate = candidatesList[0] as Map<*, *>
                    val content = candidate["content"] as Map<*, *>
                    val parts = content["parts"] as List<*>
                    val part = parts[0] as Map<*, *>
                    val summaryText = part["text"] as String

                    mapOf(
                        "summary" to summaryText,
                        "timestamp" to System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    logger.error("Error parsing Gemini API response", e)
                    mapOf(
                        "error" to "Failed to parse summary response: ${e.message}",
                        "rawResponse" to responseMap
                    )
                }
            }
        } finally {
            // Ensure resources are cleaned up
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    /**
     * Generates diversity scores based on the video summary using Gemini API.
     * This approach is much more concise than calculating diversity metrics directly.
     *
     * @param summary The text summary of the video
     * @return Map containing diversity metrics
     */
    fun generateDiversityScoresFromSummary(summary: String): Map<String, Any> {
        // Create a new client for this request
        val client = createHttpClient()

        // Build a prompt that asks Gemini to analyze the summary for diversity metrics
        val prompt = """
        Based on the following video summary, please provide diversity metrics on a scale from 0.0 to 1.0:
        
        SUMMARY:
        $summary
        
        Generate ONLY a JSON object with the following structure. Do not include any explanations:
        
        {
          "diversity_scores": {
            "crowd_diversity": {
              "age_group_variation": [float 0.0-1.0],
              "gender_distribution": [float 0.0-1.0],
              "ethnic_diversity": [float 0.0-1.0]
            },
            "behavioral_diversity": {
              "movement_variation": [float 0.0-1.0],
              "activity_mix": [float 0.0-1.0],
              "group_vs_individual_ratio": [float 0.0-1.0]
            },
            "environmental_diversity": {
              "location_type_variation": [float 0.0-1.0],
              "lighting_conditions": [float 0.0-1.0]
            },
            "overall_diversity_score": [float 0.0-1.0]
          }
        }
        
        Use these guidelines for scoring:
        - 0.0 means no diversity (e.g., only one age group)
        - 0.5 means moderate diversity (e.g., 2-3 age groups with some imbalance)
        - 1.0 means high diversity (e.g., all age groups equally represented)
        
        If the summary does not mention specific elements, assign a score of 0.0 for those elements.
    """.trimIndent()

        val requestBodyMap = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf(
                            "text" to prompt
                        )
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.1,  // Lower temperature for more consistent outputs
                "topK" to 40,
                "topP" to 0.95,
                "maxOutputTokens" to 1024
            )
        )

        val requestBodyJson = objectMapper.writeValueAsString(requestBodyMap)
        val requestBody = requestBodyJson.toRequestBody(jsonMediaType)

        // Create the request
        val request = Request.Builder()
            .url("$geminiApiUrl?key=$geminiApiKey")
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        // Execute the request
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.error("Gemini API call for diversity scores failed: ${response.code} - ${response.message}")
                    return generateEmptyDiversityScores()
                }

                val responseBody = response.body?.string() ?: throw IOException("Empty response from Gemini API")
                val responseMap = objectMapper.readValue(responseBody, Map::class.java) as Map<*, *>

                // Extract the text content from Gemini response
                try {
                    val candidatesList = responseMap["candidates"] as List<*>
                    val candidate = candidatesList[0] as Map<*, *>
                    val content = candidate["content"] as Map<*, *>
                    val parts = content["parts"] as List<*>
                    val part = parts[0] as Map<*, *>
                    val jsonText = part["text"] as String

                    // Extract only JSON from the response
                    val jsonPattern = """\{[\s\S]*}""".toRegex()
                    val matchResult = jsonPattern.find(jsonText)

                    if (matchResult != null) {
                        val jsonContent = matchResult.value
                        val diversityData = objectMapper.readValue<Map<String, Any>>(jsonContent)

                        // Return just the diversity_scores part if present
                        (diversityData["diversity_scores"] as? Map<String, Any>) ?: generateEmptyDiversityScores()
                    } else {
                        logger.error("Failed to extract JSON from diversity scores response")
                        generateEmptyDiversityScores()
                    }
                } catch (e: Exception) {
                    logger.error("Error parsing diversity scores response", e)
                    generateEmptyDiversityScores()
                }
            }
        } catch (e: Exception) {
            logger.error("Error generating diversity scores", e)
            generateEmptyDiversityScores()
        } finally {
            // Ensure resources are cleaned up
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    /**
     * Generates an empty diversity scores structure for cases where calculation fails
     */
    private fun generateEmptyDiversityScores(): Map<String, Any> {
        return mapOf(
            "crowd_diversity" to mapOf(
                "age_group_variation" to 0.0,
                "gender_distribution" to 0.0,
                "ethnic_diversity" to 0.0
            ),
            "behavioral_diversity" to mapOf(
                "movement_variation" to 0.0,
                "activity_mix" to 0.0,
                "group_vs_individual_ratio" to 0.0
            ),
            "environmental_diversity" to mapOf(
                "location_type_variation" to 0.0,
                "lighting_conditions" to 0.0
            ),
            "overall_diversity_score" to 0.0
        )
    }
}