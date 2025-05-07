package com.vision.insights.controller

import com.vision.insights.service.UploadService
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File

@RestController
@RequestMapping("/api/pinata")
class PinataController(private val uploadService: UploadService) {

    @PostMapping("/group")
    fun createGroup(@RequestParam name: String): String {
        return uploadService.createGroup(name)
    }

    @PostMapping("/upload")
    fun uploadFile(
        @RequestParam file: MultipartFile,
        @RequestParam name: String,
        @RequestParam groupId: String
    ): String {
        // Convert MultipartFile to File
        val tempFile = File.createTempFile("upload", file.originalFilename)
        file.transferTo(tempFile)

        return try {
            uploadService.uploadFile(tempFile, name, groupId)
        } finally {
            tempFile.delete() // Clean up temp file
        }
    }

    @GetMapping("/files")
    fun listFiles(): String {
        return uploadService.listFiles()
    }

    @GetMapping("/files/{id}")
    fun getFileById(@PathVariable id: String): String {
        return uploadService.getFileById(id)
    }

    @DeleteMapping("/files/{id}")
    fun deleteFile(@PathVariable id: String): String {
        return uploadService.deleteFile(id)
    }
}