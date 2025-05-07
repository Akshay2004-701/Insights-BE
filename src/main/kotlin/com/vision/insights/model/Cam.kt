package com.vision.insights.model

import org.springframework.data.mongodb.core.geo.GeoJsonPoint
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "cams")
data class Cam(
    val camId:String = UUID.randomUUID().toString(),
    val owner:String,
    val location:GeoJsonPoint,
    val footages:List<Footage>,
    val createdAt: Instant = Instant.now()
)
