package com.example.visa.dataclasses

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

import com.example.visa.accessibility.ScreenAccessibilityService
import com.example.visa.AppContainer
import com.example.visa.util.JsonUtils

enum class AssistMode {
    IDLE,
    SCREEN,
    CAMERA
}

class Assistant {
    val screenInitialGuide: String = """
        How to Use Screen Assistant

        1. Tap the bubble when you need help.
        2. Enter your goal and tap Continue.
        3. The assistant reads the screen and highlights the next target.
        4. Tap Execute if you want the assistant to run the action.
        5. Press and hold the bubble to stop.

        The assistant never acts without your approval.
        Tap ? to see this guide again.
    """.trimIndent()

    val greeting: String = "How can I help you?"

    var isRunning: Boolean = false
        private set

    var mode: AssistMode = AssistMode.IDLE
        private set

    var lastMessage: String? = null
        private set

    fun start(mode: AssistMode) {
        isRunning = true
        this.mode = mode
    }

    fun stop() {
        isRunning = false
        this.mode = AssistMode.IDLE
    }

    fun speak(message: String) {
        lastMessage = message.trim()
        // call TTSManager
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun requestNextAction(userGoal: String?): RecommendedAction? {
        // TODO: consider screenshot capture, OCR from screen

        // 0. get current UI elements from AccessibilityService
        val accessibilityService = ScreenAccessibilityService.instance

        if (accessibilityService == null) {
            Log.d("ScreenAssistant", "AccessibilityService instance is null")
            return null
        }

        val uiElements = accessibilityService.getCurrentUIElements()
        Log.d("ScreenAssistant", "UI element count: ${uiElements.size}")

        val imageBytes = accessibilityService.takeScreenshotBytes()

        if (imageBytes == null) {
            Log.d("ScreenAssistant", "Screenshot failed. Continue with text only.")
        } else {
            Log.d("ScreenAssistant", "Screenshot size: ${imageBytes.size} bytes")
        }


        // 1. prepare screen data
        val screenContext = ScreenContext(
            uies = uiElements,
            texts = OCRResult(emptyList()),
            userGoal = userGoal
        )

        return try {
            Log.d("VLM Processing", "Sending ScreenContext to VLM...")

            // 2. send screen data to VLM/server
            val result = AppContainer.visualAnalyzer.getNextAction(screenContext, imageBytes=imageBytes) // result is JSON file
            Log.d("VLM Processing", "VLM result: $result")

            // 3. parse JSON to RecommendedAction and return it
            val recommendedAction = JsonUtils.recommendedActionFromJson(result.toString())
            return recommendedAction

        } catch (e: Exception) {
            Log.e("ScreenAssistant", "Error: ${e.message}", e)
            null
        }
    }

    fun generateExplanation(action: RecommendedAction): String {
        val targetLabel = action.targetText
            ?: action.targetContentDescription
            ?: ""


        return when (action.action) {
            "ACTION_CLICK" -> {
                if (targetLabel.isNotBlank()) { "Tap \"$targetLabel\"." }
                else {"Tap the highlighted area."}

            }

            "ACTION_SET_TEXT" -> {
                if (!action.inputText.isNullOrBlank()) { "Enter \"${action.inputText}\"." }
                else if (targetLabel.isNotBlank()) { "Enter the information in \"$targetLabel\"." }
                else { "Enter the required information." }
            }

            "ACTION_SCROLL_DOWN" -> { "Scroll down." }

            "ACTION_SCROLL_UP" -> { "Scroll up." }

            "ACTION_SCROLL_LEFT" -> { "Scroll left." }

            "ACTION_SCROLL_RIGHT" -> { "Scroll right." }

            "GLOBAL_ACTION_BACK" -> { "Go back." }

            else -> { "Follow the highlighted area." }
        }
    }

}

