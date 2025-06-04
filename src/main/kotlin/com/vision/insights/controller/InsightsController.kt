package com.vision.insights.controller

import com.vision.insights.model.Insights
import com.vision.insights.repository.InsightsRepo
import com.vision.insights.service.FootageService
import com.vision.insights.service.analysis.VideoAnalysisService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("api/insights")
class InsightsController(
    private val videoAnalysisService: VideoAnalysisService,
    private val footageService: FootageService,
    private val insightsRepo: InsightsRepo
){
    @GetMapping("/{id}") // footage id
    fun generateInsights(@PathVariable id:String):Insights{
        val footage = footageService.getFootageById(id)
        val insights = videoAnalysisService.analyzeVideo(footage.footageUrl)
        return insightsRepo.save(
            Insights(
                footageId = id,
                insights = insights
            )
        )
    }

    @GetMapping("/scores")
    fun getScores(@RequestParam id:String):Map<String,Any?>{
        val insight = insightsRepo.findById(id).get()
        val score = videoAnalysisService.generateDiversityScoresFromSummary(
            insight.insights["summaryReport"].toString()
        )
        return score
    }
}