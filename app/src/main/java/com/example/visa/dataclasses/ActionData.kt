package com.example.visa.dataclasses

data class RecommendedAction(
    val action: String,
    val targetText: String? = null,
    val targetContentDescription: String? = null,
    val targetClassName: String? = null,
    val inputText: String? = null,
)

data class ActionExecutionResult(
    val success: Boolean,
    val message: String? = null
)