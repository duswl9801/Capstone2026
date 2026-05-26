package com.example.visa.util

import com.example.visa.dataclasses.BoundingBox
import com.example.visa.dataclasses.DetectedText
import com.example.visa.dataclasses.OCRResult
import com.example.visa.dataclasses.RecommendedAction
import com.example.visa.dataclasses.ScreenContext
import com.example.visa.dataclasses.UIElement
import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {

    fun recommendedActionFromJson(resultString: String): RecommendedAction? {
        return try {
            val json = JSONObject(resultString)

            val actionJson = if (json.has("response")) {
                JSONObject(json.optString("response"))
            } else {
                json
            }

            RecommendedAction(
                action = actionJson.optString("action", ""),
                targetText = actionJson.optNullableString("targetText"),
                targetContentDescription = actionJson.optNullableString("targetContentDescription"),
                targetClassName = actionJson.optNullableString("targetClassName"),
                inputText = actionJson.optNullableString("inputText")
            )

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun screenContextToJson(screenContext: ScreenContext): JSONObject {
        return JSONObject().apply {
            put("uies", uiElementsToJsonArray(screenContext.uies))
            put("texts", ocrResultToJson(screenContext.texts))
            put("userGoal", screenContext.userGoal)
            put("imgBase64", screenContext.imgBase64)
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
            put("text", ui.text)
            put("contentDescription", ui.contentDescription)
            put("className", ui.className)
            put("packageName", ui.packageName)
            put("clickable", ui.clickable)
            put("editable", ui.editable)
            put("bounds", ui.bounds)
        }
    }

    fun ocrResultToJson(ocrResult: OCRResult): JSONObject {
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
            put("confidence", detectedText.confidence)
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

    private fun JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

}