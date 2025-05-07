package com.vision.insights.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "users")
data class User(
    @Id val id: String = UUID.randomUUID().toString(),
    val walletAddress: String,
    val email: String? = null,
    val totalRewards: Double = 0.0,
    val registeredAt: Instant = Instant.now()
)
