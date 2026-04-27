package com.example.visa.dataclasses

data class RecommendedAction(
    val actionType: String,
    val targetLabel: String,
    val inputText: String,
    val targetRegion: BoundingBox
)