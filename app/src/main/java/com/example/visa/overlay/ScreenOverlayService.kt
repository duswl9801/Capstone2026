package com.example.visa.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import com.example.visa.MainActivity
import com.example.visa.R
import kotlin.math.hypot
import android.app.AlertDialog
import android.graphics.Color
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import org.json.JSONObject


import com.example.visa.AppContainer
import com.example.visa.analyzer.VisualAnalyzer
import com.example.visa.dataclasses.ScreenContext
import com.example.visa.dataclasses.OCRResult
import android.util.Log
import android.view.View
import com.example.visa.accessibility.ScreenAccessibilityService
import com.example.visa.dataclasses.BoundingBox

class ScreenOverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private lateinit var edgeGlowView: EdgeGlowView
    private lateinit var edgeGlowParams: WindowManager.LayoutParams

    private lateinit var bubbleView: AssistantBubbleView
    private lateinit var bubbleParams: WindowManager.LayoutParams

    //private lateinit var highlightOverlayView: HighlightOverlayView
    //private lateinit var highlightOverlayParams: WindowManager.LayoutParams
    private var targetBoxView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var analyzer: VisualAnalyzer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var resultView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isDragging = false
    private var isLongPressTriggered = false

    private val longPressDelay = 600L

    private val longPressRunnable = Runnable {
        if (!isDragging) {
            isLongPressTriggered = true
            returnToMainAndStop()
        }
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        analyzer = AppContainer.visualAnalyzer

        addEdgeGlow()
        //addHighlightOverlay()
        addAssistantBubble()
    }

    private fun addEdgeGlow() {
        edgeGlowView = EdgeGlowView(this)

        edgeGlowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(edgeGlowView, edgeGlowParams)
    }

    private fun addAssistantBubble() {
        bubbleView = LayoutInflater.from(this)
            .inflate(R.layout.view_assistant_bubble, null) as AssistantBubbleView

        // to locate bubble bottom-right
        val bubbleSize = dp(100)
        val marginEnd = dp(20)
        val marginBottom = dp(120)

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        bubbleParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - bubbleSize - marginEnd
            y = screenHeight - bubbleSize - marginBottom
        }

        setupBubbleTouch()
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun setupBubbleTouch() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        bubbleView.setOnClickListener {
            handleBubbleClick()
        }

        bubbleView.setOnTouchListener { view, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    isDragging = false
                    isLongPressTriggered = false

                    handler.postDelayed(longPressRunnable, longPressDelay)

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val distance = hypot(dx, dy)

                    if (distance > touchSlop) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)

                        bubbleParams.x = initialX + dx.toInt()
                        bubbleParams.y = initialY + dy.toInt()

                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)

                    if (!isDragging && !isLongPressTriggered) {
                        view.performClick()
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    true
                }

                else -> false
            }
        }
    }

    private fun handleBubbleClick() {
        showGoalInputDialog()
    }

    private fun showGoalInputDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_goal_input, null)

        val editGoal = dialogView.findViewById<EditText>(R.id.editGoal)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitGoal)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelGoal)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

        btnSubmit.setOnClickListener {
            val goal = editGoal.text.toString().trim()

            if (goal.isEmpty()) {
                Toast.makeText(this, "Please type your goal first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()

            startScreenAssistant(goal)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            editGoal.requestFocus()

            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        }

        dialog.show()
    }

    private fun startScreenAssistant(goal: String) {
        // Next step:
        // 1. capture screenshot

        Log.d("ScreenAssistant", "Goal received: $goal")

        // 2. get current UI elements from AccessibilityService
        val accessibilityService = ScreenAccessibilityService.instance

        if (accessibilityService == null) {
            Log.d("ScreenAssistant", "AccessibilityService instance is null")
            return
        }

        val uiElements = accessibilityService.getCurrentUIElements()
        Log.d("ScreenAssistant", "UI element count: ${uiElements.size}")

        // 3. send screenshot + goal + ui elements to VLM/server
        val screenContext = ScreenContext(
            uies = uiElements,
            texts = OCRResult(emptyList()),
            userGoal = goal,
            screenSummary = ""
        )

        serviceScope.launch {
            try {
                Log.d("ActionServer", "Sending ScreenContext to VLM...")

                val result = analyzer.getNextAction(screenContext)

                Log.d("ActionServer", "VLM result: $result")
                showVlmResultOverlay(result.toString())

                // highlight target element
                try {
                    val json = JSONObject(result.toString())

                    val actionJson = if (json.has("response")) {
                        JSONObject(json.optString("response"))
                    } else {
                        json
                    }

                    val boundsString = actionJson.optString("target_bounds", null)

                    Log.d("ScreenAssistant", "target_bounds: $boundsString")

                    showTargetHighlight(boundsString)
                } catch (e: Exception) {
                    Log.e("ScreenAssistant", "Failed to parse target bounds", e)
                }


            } catch (e: Exception) {
                Log.e("ActionServer", "Error: ${e.message}", e)
            }
        }

        // 4. receive next action
        // 5. highlight target or execute after user approval
    }

    private fun returnToMainAndStop() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        startActivity(intent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacks(longPressRunnable)
        serviceScope.cancel()

        if (::bubbleView.isInitialized) {
            try {
                windowManager.removeView(bubbleView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (::edgeGlowView.isInitialized) {
            try {
                windowManager.removeView(edgeGlowView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        targetBoxView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            targetBoxView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun showVlmResultOverlay(result: String) {
        resultView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))

            background = GradientDrawable().apply {
                setColor(Color.argb(235, 255, 248, 236)) // soft beige
                cornerRadius = dp(22).toFloat()
                setStroke(dp(2), Color.rgb(120, 90, 65))
            }
        }

        val titleView = TextView(this).apply {
            text = "Assistant Suggestion"
            textSize = 18f
            setTextColor(Color.rgb(80, 55, 35))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val resultTextView = TextView(this).apply {
            text = result
            textSize = 16f
            setTextColor(Color.rgb(60, 45, 35))
            setPadding(0, dp(10), 0, 0)
        }

        val closeView = TextView(this).apply {
            text = "Close"
            textSize = 15f
            setTextColor(Color.rgb(90, 65, 45))
            setPadding(0, dp(14), 0, 0)
            setOnClickListener {
                resultView?.let { view ->
                    try {
                        windowManager.removeView(view)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    resultView = null
                }
            }
        }

        container.addView(titleView)
        container.addView(resultTextView)
        container.addView(closeView)

        val params = WindowManager.LayoutParams(
            dp(320),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(90)
        }

        resultView = container

        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            Log.e("ScreenAssistant", "Failed to show VLM result overlay", e)
        }
    }

    private fun parseBoundsToBoundingBox(bounds: String?): BoundingBox? {
        if (bounds.isNullOrBlank()) return null

        val regex = Regex("""\[(\-?\d+),(\-?\d+)\]\[(\-?\d+),(\-?\d+)\]""")
        val match = regex.find(bounds) ?: return null

        return BoundingBox(
            x1 = match.groupValues[1].toInt(),
            y1 = match.groupValues[2].toInt(),
            x2 = match.groupValues[3].toInt(),
            y2 = match.groupValues[4].toInt()
        )
    }

    private fun showTargetHighlight(bounds: String?) {
        val box = parseBoundsToBoundingBox(bounds)

        if (box == null) {
            Log.d("ScreenAssistant", "No valid target bounds")
            return
        }

        val padding = dp(12)

        val left = (box.x1 - padding).coerceAtLeast(0)
        val top = (box.y1 - padding).coerceAtLeast(0)
        val right = box.x2 + padding
        val bottom = box.y2 + padding

        if (box == null) {
            Log.d("ScreenAssistant", "No valid target bounds")
            return
        }

        targetBoxView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val boxView = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(75, 255, 230, 90))
                setStroke(dp(1), Color.argb(90, 255, 180, 40))
                cornerRadius = dp(14).toFloat()
            }
        }

        val params = WindowManager.LayoutParams(
            right - left,
            bottom - top,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left
            y = top
        }

        targetBoxView = boxView
        windowManager.addView(boxView, params)

        handler.postDelayed({
            targetBoxView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                targetBoxView = null
            }
        }, 5000L)
    }


}