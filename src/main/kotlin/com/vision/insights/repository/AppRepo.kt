package com.vision.insights.repository

import com.vision.insights.model.Cam
import com.vision.insights.model.Footage
import com.vision.insights.model.Insights
import com.vision.insights.model.User
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserRepo:MongoRepository<User,String>{
    @Query("{ 'walletAddress' : ?0 }")
    fun findByWalletAddress(walletAddress:String):User?
}

@Repository
interface CamRepo:MongoRepository<Cam,String> {
    @Query("{ 'owner' : ?0 }")
    fun findAllByOwner(owner: String): List<Cam>
}

@Repository
interface FootageRepo:MongoRepository<Footage,String>{
    @Query("{ 'camId' : ?0 }")
    fun findAllByCamId(camId:String):List<Footage>
}

@Repository
interface InsightsRepo:MongoRepository<Insights,String>
