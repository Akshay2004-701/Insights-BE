package com.vision.insights.service

import com.vision.insights.model.Footage
import com.vision.insights.repository.FootageRepo
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant

@Service
class FootageService (
    private val footageRepo: FootageRepo,
    private val uploadService: UploadService,
    private val camService: CamService
){
    fun getAllFootages(camId:String):List<Footage> = footageRepo.findAllByCamId(camId)

    fun getFootageById(footageId:String):Footage = footageRepo.findById(footageId).get()

    fun uploadFootage(file:File, camId:String, duration:Int, timestamp:String):String = try{
        val cam = camService.getCamById(camId)
        val footageUrl = uploadService.uploadFile(
            file = file,
            name = camId,
            groupId = cam.groupId
        )
        val footage = Footage(
            camId = camId,
            footageUrl = footageUrl,
            durationSeconds = duration,
            timestamp = Instant.parse(timestamp)
        )
        footageRepo.save(footage)
        "Footage successfully uploaded"
    } catch (e:Exception){
        e.localizedMessage
    }
}