package com.vision.insights.controller

import com.vision.insights.model.Footage
import com.vision.insights.service.FootageService
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File

@RestController
@RequestMapping("/api/footages")
class FootageController(private val footageService: FootageService) {

    @GetMapping("/{camId}")
    fun getAllFootages(@PathVariable camId: String): List<Footage> =
        footageService.getAllFootages(camId)

    @GetMapping("/detail/{footageId}")
    fun getFootageById(@PathVariable footageId: String): Footage =
        footageService.getFootageById(footageId)

    @PostMapping("/upload")
    fun uploadFootage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("camId") camId: String,
        @RequestParam("duration") duration: Int,
        @RequestParam("timestamp") timestamp: String
    ): String {

            // Convert MultipartFile to File
            val tempFile = File(System.getProperty("java.io.tmpdir"), file.originalFilename ?: "uploadedFile")
            file.transferTo(tempFile)

            // Call the service method to upload footage
            return footageService.uploadFootage(tempFile, camId, duration, timestamp)
    }
}