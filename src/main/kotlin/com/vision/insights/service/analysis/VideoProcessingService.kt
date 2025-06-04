package com.vision.insights.service.analysis

import org.springframework.stereotype.Service
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO
import org.slf4j.LoggerFactory
import org.springframework.util.FileSystemUtils

@Service
class VideoProcessingService {
    private val logger = LoggerFactory.getLogger(VideoProcessingService::class.java)

    /**
     * Extracts frames from a video at 1 frame per second and returns them as PNG images
     * Uses JavaCV instead of external FFmpeg executable
     *
     * @param videoUrl URL of the video to process
     * @return Array of ByteArray where each element is a PNG image
     */
    fun extractFramesAtOneSecondInterval(videoUrl: String): Array<ByteArray> {
        // Create temporary working directory
        val workingDir = Files.createTempDirectory("video-processing-${UUID.randomUUID()}")

        try {
            // Download video with enhanced error handling
            logger.info("Downloading video from URL: $videoUrl")
            val videoFile = downloadVideo(videoUrl, workingDir)
            logger.info("Video downloaded to: ${videoFile.absolutePath}")

            // Create output directory for frames (for temp storage if needed)
            val framesDir = workingDir.resolve("frames")
            Files.createDirectories(framesDir)

            // Use JavaCV to extract frames
            val framesList = mutableListOf<ByteArray>()
            extractFramesWithJavaCV(videoFile, framesList)

            logger.info("Successfully extracted ${framesList.size} frames from video")

            return framesList.toTypedArray()
        } catch (e: Exception) {
            logger.error("Error processing video", e)
            throw e
        } finally {
            // Clean up temporary files
            try {
                FileSystemUtils.deleteRecursively(workingDir)
                logger.info("Cleaned up temporary directory: $workingDir")
            } catch (e: IOException) {
                logger.warn("Failed to clean up temporary directory: $workingDir", e)
            }
        }
    }

    /**
     * Extract frames from video at 1fps using JavaCV
     */
    private fun extractFramesWithJavaCV(videoFile: File, framesList: MutableList<ByteArray>) {
        val frameGrabber = FFmpegFrameGrabber(videoFile)
        val converter = Java2DFrameConverter()

        try {
            // Start the frame grabber
            frameGrabber.start()

            logger.info("Video information - Duration: ${frameGrabber.lengthInTime / 1000000} seconds, " +
                    "Frame rate: ${frameGrabber.frameRate}, " +
                    "Video codec: ${frameGrabber.videoCodec}")

            // Get video frame rate for FPS calculation
            val frameRate = frameGrabber.frameRate
            val totalFrames = frameGrabber.lengthInFrames
            val totalSeconds = (totalFrames / frameRate).toInt()

            logger.info("Estimated total seconds: $totalSeconds")

            // Extract one frame per second
            for (second in 0 until totalSeconds) {
                // Calculate the frame number for this second
                val targetFrameNumber = (second * frameRate).toLong()

                // Set the frame number
                frameGrabber.setVideoFrameNumber(targetFrameNumber.toInt())

                // Grab the frame
                val frame = frameGrabber.grabImage() ?: continue

                // Convert the frame to a BufferedImage
                val bufferedImage = converter.convert(frame)

                if (bufferedImage != null) {
                    // Convert the BufferedImage to a ByteArray in PNG format
                    val frameBytes = convertBufferedImageToByteArray(bufferedImage)
                    framesList.add(frameBytes)

                    logger.debug("Extracted frame at second $second")
                }
            }

        } catch (e: Exception) {
            logger.error("Error extracting frames with JavaCV", e)
            throw RuntimeException("Failed to extract frames using JavaCV: ${e.message}", e)
        } finally {
            try {
                // Release resources
                converter.close()
                frameGrabber.stop()
                frameGrabber.release()
            } catch (e: Exception) {
                logger.warn("Error closing JavaCV resources", e)
            }
        }
    }

    /**
     * Alternative method that's simpler but less precise with timing
     */
    private fun extractFramesWithJavaCVSimple(videoFile: File, framesList: MutableList<ByteArray>) {
        val frameGrabber = FFmpegFrameGrabber(videoFile)
        val converter = Java2DFrameConverter()

        try {
            frameGrabber.start()

            val frameRate = frameGrabber.frameRate
            logger.info("Video frame rate: $frameRate")

            // Calculate how many frames to skip to get 1 frame per second
            val framesToSkip = frameRate.toInt()
            var frameNumber = 0

            var frame = frameGrabber.grabImage()
            while (frame != null) {
                // Process only 1 frame per second
                if (frameNumber % framesToSkip == 0) {
                    val bufferedImage = converter.convert(frame)
                    if (bufferedImage != null) {
                        val frameBytes = convertBufferedImageToByteArray(bufferedImage)
                        framesList.add(frameBytes)
                        logger.debug("Extracted frame: ${framesList.size}")
                    }
                }

                frameNumber++
                frame = frameGrabber.grabImage()
            }

            logger.info("Total extracted frames: ${framesList.size}")
        } catch (e: Exception) {
            logger.error("Error extracting frames with JavaCV", e)
            throw RuntimeException("Failed to extract frames using JavaCV: ${e.message}", e)
        } finally {
            try {
                converter.close()
                frameGrabber.stop()
                frameGrabber.release()
            } catch (e: Exception) {
                logger.warn("Error closing JavaCV resources", e)
            }
        }
    }

    /**
     * Converts a BufferedImage to a ByteArray in PNG format
     */
    private fun convertBufferedImageToByteArray(image: BufferedImage): ByteArray {
        val outputStream = ByteArrayOutputStream()

        if (!ImageIO.write(image, "png", outputStream)) {
            throw IOException("Failed to write image to PNG format")
        }

        return outputStream.toByteArray()
    }

    /**
     * Downloads a video from the given URL to a temporary file with enhanced error handling
     */
    private fun downloadVideo(videoUrl: String, workingDir: Path): File {
        val videoFile = workingDir.resolve("input_video.mp4").toFile() // Added extension for better format detection
        var connection: HttpURLConnection? = null

        try {
            val url = URL(videoUrl)
            connection = url.openConnection() as HttpURLConnection

            // Set appropriate headers for IPFS/Pinata gateway
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 30000 // 30 seconds timeout
            connection.readTimeout = 60000    // 60 seconds read timeout

            val responseCode = connection.responseCode
            logger.info("URL connection response code: $responseCode")

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Failed to download video. HTTP response code: $responseCode")
            }

            // Get content type to verify it's actually a video
            val contentType = connection.contentType
            logger.info("Content type of downloaded file: $contentType")

            if (contentType != null && !contentType.startsWith("video/")) {
                logger.warn("The URL might not point to a video file. Content-Type: $contentType")
            }

            // Copy the input stream to the file
            connection.inputStream.use { input ->
                Files.copy(input, videoFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            // Verify the file was actually downloaded and has content
            if (!videoFile.exists() || videoFile.length() == 0L) {
                throw IOException("Failed to download video or video file is empty")
            }

            logger.info("Successfully downloaded video file (${videoFile.length()} bytes)")
            return videoFile

        } catch (e: Exception) {
            logger.error("Error downloading video from URL: $videoUrl", e)
            throw IOException("Failed to download video from URL: $videoUrl. Error: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
    }
}