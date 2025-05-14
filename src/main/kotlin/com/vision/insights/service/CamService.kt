package com.vision.insights.service

import com.vision.insights.model.Cam
import com.vision.insights.model.CamBody
import com.vision.insights.model.User
import com.vision.insights.repository.CamRepo
import com.vision.insights.repository.UserRepo
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class CamService(
    private val camRepository: CamRepo,
    private val userRepo: UserRepo,
    private val uploadService: UploadService
) {

    fun getAllCams(userId: String): List<Cam>{
        val user= userRepo.findById(userId).get()
        return camRepository.findAllByOwner(user.walletAddress)
    }

    fun getCamById(camId: String): Cam = camRepository.findById(camId).get()

    fun addCam(camBody: CamBody, userId:String): Cam{
        val user = userRepo.findById(userId).get()
        val seed = (1..8).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"[
            Random(userId.hashCode().toLong()).nextInt(62)] }.joinToString("")
        val cam = Cam(
            owner = user.walletAddress,
            location = camBody.location,
            groupId = uploadService.createGroup(seed),
            camType = Cam.CamType.OUTDOOR,
            description = camBody.description
        )
        return camRepository.save(cam)
    }

    fun updateCam(camId: String, updatedCam: Cam): Cam? {
        return if (camRepository.existsById(camId)) {
            camRepository.save(updatedCam)
        } else null
    }
}