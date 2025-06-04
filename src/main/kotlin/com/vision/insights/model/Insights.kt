package com.vision.insights.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.UUID

@Document(collection = "insights_library")
data class Insights(
    @Id
    val insId:String = UUID.randomUUID().toString(),
    val footageId:String,
    val insights:Map<String,Any?>
)
