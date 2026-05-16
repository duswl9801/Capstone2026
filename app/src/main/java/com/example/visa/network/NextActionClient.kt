package com.example.visa.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import com.example.visa.BuildConfig
import com.example.visa.dataclasses.ScreenContext

class NextActionClient (
    private val baseURL: String = BuildConfig.ACTION_SERVER_URL,
    private val apiToken: String = BuildConfig.ACTION_SERVER_TOKEN,
){
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getScreenContext(screenContext: ScreenContext): ScreenContext{
        return screenContext
    }

    fun getNextAction(
        goal: String,
        screenText: List<String>
    ): String {
        val json = JSONObject().apply {
            put("goal", goal)
            put("screen_text", JSONArray(screenText))
        }

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
            return responseJson.getString("response")
        }
    }
}
