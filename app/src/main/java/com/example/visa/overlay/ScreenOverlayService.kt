package com.example.visa.overlay

import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

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
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.EditText
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper

import com.example.visa.AppContainer
import com.example.visa.analyzer.VisualAnalyzer
import com.example.visa.util.Utils.dp

import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.example.visa.accessibility.ScreenAccessibilityService
import com.example.visa.dataclasses.Assistant
import com.example.visa.dataclasses.RecommendedAction
import com.example.visa.dataclasses.User
import com.example.visa.util.TTSManager
import com.example.visa.util.Utils
import com.example.visa.util.Utils.parseBoundsToBoundingBox

class ScreenOverlayService : Service() {

    private lateinit var windowManager: WindowManager // Android system manager managing overlay views (directly on top of other apps)
    private val handler = Handler(Looper.getMainLooper()) // thread handler
    // CoroutineScope: execute/manage coroutine tasks
    // SupervisorJob: keeps other coroutines running if one fails
    // Dispatchers.Main: run tasks on the UI thread
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var analyzer: VisualAnalyzer
    private lateinit var assistant: Assistant
    private lateinit var user: User

    private var nextAction: RecommendedAction? = null

    private var isListening = false
    private var shouldRunAfterVoiceStop = false
    private var voiceGoalText = ""
    private var speechRecognizer: SpeechRecognizer? = null

    // custom views
    private lateinit var edgeGlowView: EdgeGlowView
    private lateinit var edgeGlowParams: WindowManager.LayoutParams
    private lateinit var bubbleView: AssistantBubbleView
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var speechView: View? = null
    private var loadingView: View? = null

    private var targetBoxView: View? = null
    private var resultView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isLongPressTriggered = false
    private val longPressDelay = 600L

    // handling long press action for the bubble
    private val longPressRunnable = Runnable {
        if (!isDragging) {
            isLongPressTriggered = true
            returnToMainAndStop()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()

        TTSManager.init(this)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        analyzer = AppContainer.visualAnalyzer
        assistant  = AppContainer.assistant
        user = AppContainer.user

        addEdgeGlow()
        addAssistantBubble()
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacks(longPressRunnable)
        serviceScope.cancel()

        if (::edgeGlowView.isInitialized) {
            removeOverlayView(edgeGlowView)
        }
        if (::bubbleView.isInitialized) {
            removeOverlayView(bubbleView)
        }
        removeOverlayView(targetBoxView)
        targetBoxView = null
        removeOverlayView(speechView)
        speechView = null
        removeOverlayView(resultView)
        resultView = null
        removeOverlayView(loadingView)
        loadingView = null
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun removeOverlayView(view: View?) {
        if (view == null) return

        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    @RequiresApi(Build.VERSION_CODES.R)
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

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setupBubbleTouch() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop // threshold for distinguishing drag from tap

        // click
        bubbleView.setOnClickListener { handleBubbleClick() }

        // drag
        bubbleView.setOnTouchListener { view, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> { // touch starts
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    isDragging = false
                    isLongPressTriggered = false

                    handler.postDelayed(longPressRunnable, longPressDelay)

                    true
                }

                MotionEvent.ACTION_MOVE -> { // touch moves
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

                MotionEvent.ACTION_UP -> { // touch ends
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

    @RequiresApi(Build.VERSION_CODES.R)
    private fun handleBubbleClick()  {
        if (isVoiceModeEnabled()) {
            handleVoiceBubbleClick()
        } else {
            showGoalDialog()
        }
    }

    private fun isVoiceModeEnabled(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getBoolean("voice_mode", false)
    }

    private fun handleVoiceBubbleClick() {
        if (!isListening) {
            startVoiceInput()
        } else {
            stopVoiceInputAndRun()
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is not available.", Toast.LENGTH_SHORT).show()
            return
        }

        voiceGoalText = ""
        shouldRunAfterVoiceStop = false
        isListening = true

        setBubbleIcon(R.drawable.ic_voice)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("VoiceInput", "ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("VoiceInput", "speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("VoiceInput", "speech ended")
            }

            override fun onError(error: Int) {
                Log.d("VoiceInput", "speech error: $error")

                if (shouldRunAfterVoiceStop && voiceGoalText.isNotBlank()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        finishVoiceInputAndRun(voiceGoalText)
                    }
                } else {
                    resetVoiceInput()
                    Toast.makeText(
                        this@ScreenOverlayService,
                        "I couldn't hear that. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = matches?.firstOrNull().orEmpty()

                Log.d("VoiceInput", "voice result: $finalText")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    finishVoiceInputAndRun(finalText.ifBlank { voiceGoalText })
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull().orEmpty()

                if (partial.isNotBlank()) {
                    voiceGoalText = partial
                    Log.d("VoiceInput", "partial voice result: $voiceGoalText")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun stopVoiceInputAndRun() {
        shouldRunAfterVoiceStop = true
        isListening = false

        setBubbleIcon(R.drawable.ic_visa)

        speechRecognizer?.stopListening()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun finishVoiceInputAndRun(goal: String) {
        val trimmedGoal = goal.trim()

        resetVoiceInput()

        if (trimmedGoal.isBlank()) {
            Toast.makeText(this, "I couldn't hear your goal.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("VoiceInput", "final voice goal: $trimmedGoal")

        // show what the assistant heard
        showMessage(message="Heard: $trimmedGoal", showButtons=false)

        // wait briefly so the user can check it
        handler.postDelayed({
            runAssistantWithGoal(trimmedGoal)
        }, 1200L)
    }

    private fun resetVoiceInput() {
        isListening = false
        shouldRunAfterVoiceStop = false

        setBubbleIcon(R.drawable.ic_visa)

        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun setBubbleIcon(iconResId: Int) {
        try {
            val iconView = bubbleView.findViewById<ImageView>(R.id.visaIcon)
            iconView?.setImageResource(iconResId)
        } catch (e: Exception) {
            Log.e("VoiceInput", "Failed to update bubble icon", e)
        }
    }



    @RequiresApi(Build.VERSION_CODES.R)
    private fun showGoalDialog()  {

        val themedContext = ContextThemeWrapper(this, R.style.Theme_VisA)

        // show goal receiving dialog
        val dialogView = LayoutInflater.from(themedContext)
            .inflate(R.layout.dialog_goal_input, null)

        val editGoal = dialogView.findViewById<EditText>(R.id.editGoal)
        val btnOk = dialogView.findViewById<TextView>(R.id.btnOk)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnSpeaker = dialogView.findViewById<ImageView>(R.id.btnSpeak)
        ImageViewCompat.setImageTintList(
            btnSpeaker,
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.visa_highlight_icon))
        )
        val textDialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)

        val dialog = AlertDialog.Builder(themedContext)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        // sets the dialog as an overlay window so it can appear outside an Activity
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

        btnOk.setOnClickListener {
            val goal = editGoal.text.toString().trim()
            dialog.dismiss()
            runAssistantWithGoal(goal)
        }

        btnSpeaker.setOnClickListener { TTSManager.speak(textDialogMessage.text.toString()) }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        """
          dialog.setOnShowListener { // focus editText and open keyboard
            editGoal.requestFocus()

            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        }  
        """

        dialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun receiveNextAction(goal: String?) {

        Log.d("ScreenService", "Start receiveNectAction()...")

        // receive next action from VLM
        serviceScope.launch {
            try {
                showLoadingOverlay("Reading this screen...")

                val recommendedAction = assistant.requestNextAction(goal)

                if (recommendedAction == null) {
                    Toast.makeText(
                        this@ScreenOverlayService,
                        "Failed to parse next action.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                nextAction = recommendedAction

                // find target element from current screen
                val accessibilityService = ScreenAccessibilityService.instance

                // serch target ui in current screen
                val targetElement = accessibilityService?.giveTargetInfo(recommendedAction)
                Log.d("ScreenAssistant", "target element: $targetElement")

                // highlight target element
                highlightTarget(targetElement?.bounds)

                // assistant generate explanation
                val message = assistant.generateExplanation(recommendedAction)

                showMessage(message)


            } catch (e: Exception) {Log.e("ActionServer", "Error: ${e.message}", e)}
            finally { hideLoadingOverlay() }
        }
    }

    private fun showMessage(message: String, showButtons: Boolean = true) {

        val themedContext = ContextThemeWrapper(this, R.style.Theme_VisA)
        removeOverlayView(speechView)
        speechView = null

        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.view_assistant_speech, null)

        val buttonRow = view.findViewById<LinearLayout>(R.id.suggestionButtonRow)
        buttonRow.visibility = if (showButtons) View.VISIBLE else View.GONE

        val txtMessage = view.findViewById<TextView>(R.id.txtSuggestionMessage)
        val btnDoAction = view.findViewById<TextView>(R.id.btnDoAction)
        val btnDoMyself = view.findViewById<TextView>(R.id.btnDoMyself)
        val btnSpeaker = view.findViewById<ImageView>(R.id.btnSpeak)
        ImageViewCompat.setImageTintList(
            btnSpeaker,
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.visa_highlight_icon))
        )

        txtMessage.text = message

        btnSpeaker.setOnClickListener { TTSManager.speak(txtMessage.text.toString()) }

        btnDoAction.setOnClickListener {
            Log.d("ScreenAssistant", "Execute clicked")

            // clear overlays first
            removeOverlayView(speechView)
            speechView = null

            removeOverlayView(targetBoxView)
            targetBoxView = null

            // wait a little so the underlying app can receive the gesture cleanly
            handler.postDelayed({
                runNextStep()
            }, 50L)
        }

        btnDoMyself.setOnClickListener {
            Log.d("ScreenAssistant", "Do myself clicked")

            removeOverlayView(speechView)
            speechView = null
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(120)
        }

        speechView = view

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e("ScreenAssistant", "Failed to show assistant message", e)
        }
    }

    private fun highlightTarget(bounds: String?) {
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

        targetBoxView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val color = Color.CYAN
        val boxView = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)))
                setStroke(dp(1), color)
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

    private fun runNextStep() {
        val action = nextAction

        if (action == null) {
            Toast.makeText(this, "No action to run.", Toast.LENGTH_SHORT).show()
            Log.d("ScreenAssistant", "pendingAction is null")
            return
        }

        val service = ScreenAccessibilityService.instance

        if (service == null) {
            Toast.makeText(this, "Accessibility service is not connected.", Toast.LENGTH_SHORT).show()
            Log.d("ScreenAssistant", "AccessibilityService instance is null")
            return
        }

        val success = service.executeNextAction(action)

        if (success == null) { Toast.makeText(this, "Failed to execute action.", Toast.LENGTH_SHORT).show()}

        nextAction = null
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

    private fun showLoadingOverlay(message: String = "Reading this screen...") {
        if (loadingView != null) return

        val view = LayoutInflater.from(this)
            .inflate(R.layout.loading, null)
        view.visibility = View.VISIBLE

        val loadingText = view.findViewById<TextView>(R.id.loadingText)

        // 여기서 Utils 함수 사용
        Utils.showLoading(view, loadingText, message)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(view, params)
            loadingView = view
        } catch (e: Exception) {
            Log.e("ScreenAssistant", "Failed to show loading overlay", e)
        }
    }

    private fun hideLoadingOverlay() {
        loadingView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e("ScreenAssistant", "Failed to remove loading overlay", e)
            }
            loadingView = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun runAssistantWithGoal(goal: String) {
        val trimmedGoal = goal.trim()

        if (trimmedGoal.isEmpty()) {
            Toast.makeText(this, "Please enter your goal first.", Toast.LENGTH_SHORT).show()
            return
        }

        user.setGoal(trimmedGoal)

        receiveNextAction(user.goal)
    }

}