package com.example.visa.dataclasses

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

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

data class ActionCandidate(
    val node: AccessibilityNodeInfo,
    val text: String,
    val contentDescription: String,
    val className: String,
    val packageName: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val bounds: String,
    val boundsRect: Rect
)

