package com.example.visa.analyzer

import com.example.visa.dataclasses.*
import com.example.visa.util.JsonUtils

import android.graphics.Bitmap
import com.example.visa.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VisualAnalyzer(
    // server
    private val baseURL: String = BuildConfig.ACTION_SERVER_URL,
    private val apiToken: String = BuildConfig.ACTION_SERVER_TOKEN,

    // OCR
    private val ocrName: String,
    private val confidenceThreshold: Float,
    private val mergeDistanceThreshold_x: Int,
    private val mergeDistanceThreshold_y: Int,

    // VLM model
    private val modelName: String
){
    // HTTP client for the desktop server
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun detectText(image: Bitmap): OCRResult {
        // TODO: connect to python OCR server
        // Use ML Kit OCR for Now. Don't know later
        val image = InputImage.fromBitmap(image, 0)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText: Text = recognizer.process(image).await()

        val detectedTexts = arrayListOf<DetectedText>()

        for (block in visionText.textBlocks){
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val confidence = line.confidence

                detectedTexts.add(
                    DetectedText(text = line.text, box = BoundingBox(x1 = box.left, y1 = box.top, x2 = box.right, y2 = box.bottom), confidence = confidence)
                )
            }
        }

        val filteredTexts = detectedTexts.filter { it.confidence >= confidenceThreshold }
            .sortedWith(compareBy<DetectedText> {it.box.y1}.thenBy {it.box.x1})
            // sort texts in reading order: 1) by top to bottom 2) if y1 is similar, then by left to right

        val mergedTexts = arrayListOf<DetectedText>()

        for (text in filteredTexts) {
            val last = mergedTexts.lastOrNull() // bring last element from the text

            val shouldMerge = // true -> merge | false -> do nothing. y: vertical, x: horizental
                last != null &&
                        abs(last.box.y1 - text.box.y1) < mergeDistanceThreshold_y &&
                        text.box.x1 - last.box.x2 < mergeDistanceThreshold_x

            if (shouldMerge) {
                val merged = DetectedText(
                    text = last!!.text + " " + text.text, // merge with blank
                    box = BoundingBox(
                        x1 = minOf(last.box.x1, text.box.x1),
                        y1 = minOf(last.box.y1, text.box.y1),
                        x2 = maxOf(last.box.x2, text.box.x2),
                        y2 = maxOf(last.box.y2, text.box.y2)
                    ),
                    confidence = (last.confidence + text.confidence) / 2
                )

                mergedTexts[mergedTexts.lastIndex] = merged
            } else {
                mergedTexts.add(text)
            }
        }

        return OCRResult(mergedTexts)
    }

    suspend fun getNextAction(screenContext: ScreenContext): String = withContext(Dispatchers.IO) {

        val json = JsonUtils.screenContextToJson(screenContext)

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseURL/next-action")
            .addHeader("Authorization", "Bearer $apiToken")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw Exception("Server request failed: ${response.code}, $responseBody")
            }

            val responseJson = JSONObject(responseBody)
            responseJson.getString("response")
        }
    }

}