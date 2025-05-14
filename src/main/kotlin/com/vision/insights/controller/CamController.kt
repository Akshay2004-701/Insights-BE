package com.vision.insights.controller

import com.vision.insights.model.Cam
import com.vision.insights.model.CamBody
import com.vision.insights.model.User
import com.vision.insights.service.CamService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/cams")
class CamController(private val camService: CamService) {

    @GetMapping("/user")
    fun getAllCams(@RequestParam userId:String): List<Cam> = camService.getAllCams(userId)

    @GetMapping("/{camId}")
    fun getCamById(@PathVariable camId: String): Cam? = camService.getCamById(camId)

    @PostMapping
    fun addCam(@RequestBody cam: CamBody, @RequestParam("userId") userId:String): Cam
    = camService.addCam(cam,userId)

    @PutMapping("/{camId}")
    fun updateCam(@PathVariable camId: String, @RequestBody updatedCam: Cam): Cam? =
        camService.updateCam(camId, updatedCam)
}
