package com.example.visa.dataclasses

data class BoundingBox(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int
) {
    val width: Int
        get() = x2 - x1

    val height: Int
        get() = y2 - y1

    val center: Pair<Int, Int>
        get() = Pair((x1 + x2) / 2, (y1 + y2) / 2)
}

data class DetectedText(
    val text: String,
    val box: BoundingBox,
    val confidence: Float
)

data class OCRResult(
    val detectedTexts: List<DetectedText>
)

