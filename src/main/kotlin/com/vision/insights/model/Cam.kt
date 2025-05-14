package com.vision.insights.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.geo.GeoJsonPoint
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.*

@Document(collection = "cams")
data class Cam(
    @Id
    val camId:String = UUID.randomUUID().toString(),
    val owner:String,
    val location:GeoJsonPoint,
    val groupId:String,
    val camType:CamType,
    val description:String? = null,
    val createdAt: Instant = Instant.now()
){
    enum class CamType{INDOOR, OUTDOOR}

}
data class CamBody(
    val location:GeoJsonPoint,
    val camType: CamType,
    val description:String? = null
){
    enum class CamType{INDOOR, OUTDOOR}

}
