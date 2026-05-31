package com.example.visa.analyzer

import com.example.visa.dataclasses.*
import com.example.visa.util.JsonUtils

import android.graphics.Bitmap
import android.util.Log
import com.example.visa.BuildConfig
import com.example.visa.util.Utils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

    suspend fun detectText_ocr(image: Bitmap, language: String = "ko"): OCRResult = withContext(Dispatchers.IO) {

        val imageBytes = bitmapToJpegBytes(image, quality = 75)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "language",
                language
            )
            .addFormDataPart(
                "image",
                "camera.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            ).build()

        Log.d("OCR", "Connecting Server with multipart image")

        val request = Request.Builder()
            .url("$baseURL/text-detection")
            .addHeader("Authorization", "Bearer $apiToken")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) { throw Exception("Server request failed: ${response.code}, $responseBody") }

            val responseJson = JSONObject(responseBody)
            val textsArray = responseJson.getJSONArray("texts")
            Log.d("OCR", "textsArray came")

            val detectedTexts = arrayListOf<DetectedText>()

            for (i in 0 until textsArray.length()) {
                val item = textsArray.getJSONObject(i)
                val boxJson = item.getJSONObject("box")

                detectedTexts.add(
                    DetectedText(
                        text = item.getString("text"),
                        confidence = item.optDouble("confidence", 1.0).toFloat(),
                        box = BoundingBox(
                            x1 = boxJson.getInt("x1"),
                            y1 = boxJson.getInt("y1"),
                            x2 = boxJson.getInt("x2"),
                            y2 = boxJson.getInt("y2")
                        )
                    )
                )
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

            OCRResult(mergedTexts)
        }
    }

    suspend fun getNextAction(
        screenContext: ScreenContext,
        imageBytes: ByteArray? = null
    ): String = withContext(Dispatchers.IO) {

        Log.d("VLM Processing", "Visual Analyzer received screen context and image...")

        val contextJson = JsonUtils.screenContextToJson(screenContext).toString()

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "context",
                contextJson
            )

        if (imageBytes != null) {
            multipartBuilder.addFormDataPart(
                "image",
                "screenshot.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
        }

        val body = multipartBuilder.build()

        val request = Request.Builder()
            .url("$baseURL/next-action")
            .addHeader("Authorization", "Bearer $apiToken")
            .post(body)
            .build()

        Log.d("VLM Processing", "Request made and connect to server...")

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) { throw Exception("Server request failed: ${response.code}, $responseBody") }

            val responseJson = JSONObject(responseBody)
            responseJson.getString("response")
        }
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 75): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

}