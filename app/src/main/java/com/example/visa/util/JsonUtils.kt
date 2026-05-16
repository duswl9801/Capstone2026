package com.example.visa.util

import com.example.visa.dataclasses.BoundingBox
import com.example.visa.dataclasses.DetectedText
import com.example.visa.dataclasses.OCRResult
import com.example.visa.dataclasses.ScreenContext
import com.example.visa.dataclasses.UIElement
import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {

    fun screenContextToJson(screenContext: ScreenContext): JSONObject {
        return JSONObject().apply {
            put("userGoal", screenContext.userGoal)
            put("screenSummary", screenContext.screenSummary)
            put("uies", uiElementsToJsonArray(screenContext.uies))
            put("texts", ocrResultToJson(screenContext.texts))
            put("highlightedElements", boundingBoxesToJsonArray(screenContext.highlightedElements))
        }
    }

    private fun uiElementsToJsonArray(uies: List<UIElement>): JSONArray {
        return JSONArray().apply {
            uies.forEach { ui ->
                put(uiElementToJson(ui))
            }
        }
    }

    private fun uiElementToJson(ui: UIElement): JSONObject {
        return JSONObject().apply {
            put("id", ui.id)
            put("text", ui.text)
            put("contentDescription", ui.contentDescription)
            put("className", ui.className)
            put("packageName", ui.packageName)
            put("clickable", ui.clickable)
            put("editable", ui.editable)
            put("bounds", ui.bounds)
        }
    }

    private fun ocrResultToJson(ocrResult: OCRResult): JSONObject {
        return JSONObject().apply {
            put("detectedTexts", detectedTextsToJsonArray(ocrResult.detectedTexts))
        }
    }

    private fun detectedTextsToJsonArray(detectedTexts: List<DetectedText>): JSONArray {
        return JSONArray().apply {
            detectedTexts.forEach { detectedText ->
                put(detectedTextToJson(detectedText))
            }
        }
    }

    private fun detectedTextToJson(detectedText: DetectedText): JSONObject {
        return JSONObject().apply {
            put("text", detectedText.text)
            put("box", boundingBoxToJson(detectedText.box))
            put("confidence", detectedText.confidence.toString())
        }
    }

    private fun boundingBoxesToJsonArray(boxes: List<BoundingBox>): JSONArray {
        return JSONArray().apply {
            boxes.forEach { box ->
                put(boundingBoxToJson(box))
            }
        }
    }

    private fun boundingBoxToJson(box: BoundingBox): JSONObject {
        return JSONObject().apply {
            put("x1", box.x1)
            put("y1", box.y1)
            put("x2", box.x2)
            put("y2", box.y2)
        }
    }
}