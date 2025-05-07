package com.vision.insights.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document
data class Footage(
    @Id val id: String = UUID.randomUUID().toString(),
    val camId: String,
    val footageUrl: String,
    val timestamp: Instant,
    val durationSeconds: Int,
    val verified: Boolean = false,
    val rewardAmount: Double = 0.0,
    val status: FootageStatus = FootageStatus.PENDING,
    val submittedAt: Instant = Instant.now()
) {
    enum class FootageStatus { PENDING, APPROVED, REJECTED }
}
