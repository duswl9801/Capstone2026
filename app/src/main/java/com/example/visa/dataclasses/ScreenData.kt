package com.example.visa.dataclasses

// based on the accessibility service results
data class UIElement(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val packageName: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val bounds: String
)

data class ScreenContext(
    val uies: List<UIElement>,
    val texts: OCRResult,
    val userGoal: String?
    //val imgBytes: ByteArray?   -> bytes should be separated
)