package com.example.visa

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

import com.example.visa.dataclasses.BoundingBox
import com.example.visa.dataclasses.DetectedText
import com.example.visa.dataclasses.OCRResult
import com.example.visa.util.TTSManager

class ShutterActivity : BaseActivity() {
    // flat string built from the OCR JSON array, ready to be spoken
    private var ocrTexts: String = ""
    private var detectedTexts: List<DetectedText> = emptyList()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_shutter)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.shutter)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        TTSManager.init(this)

        val resultImageView = findViewById<ImageView>(R.id.resultImageView)
        resultImageView.isClickable = true
        val btnSpeak        = findViewById<ImageView>(R.id.btnSpeak)
        val btnGallery = findViewById<ImageView>(R.id.btnGallery)
        btnGallery.visibility = View.GONE // make it visible when the function implemented

        findViewById<View>(R.id.topBackBar).setOnClickListener {
            finish()
        }

        btnGallery.setOnClickListener {
            // TODO: open gallery
        }

        // load image
        val imagePath = intent.getStringExtra("imagePath")
        if (imagePath != null) {
            resultImageView.setImageBitmap(BitmapFactory.decodeFile(imagePath))
        }

        // Parse OCR text from JSON array extra
        val ocrJson = intent.getStringExtra("ocrText")
        val ocrResult = parseOcrText(ocrJson)

        detectedTexts = ocrResult.detectedTexts
        ocrTexts = detectedTexts.joinToString(". ") { it.text.trim() }

        // tap to speak, tap again to stop
        btnSpeak.setOnClickListener {
            if (ocrTexts.isBlank()) {
                TTSManager.speak("No text was detected in this image.")
            } else {
                TTSManager.speak(ocrTexts)
            }
        }

        resultImageView.setOnTouchListener { view, event ->
            if(event.action == MotionEvent.ACTION_UP) {
                view.performClick()

                val point = mapTouchToImagePoint(resultImageView, event.x, event.y)

                if (point != null) {
                    val imageX = point.first
                    val imageY = point.second

                    val touchedText = detectedTexts.firstOrNull { detected -> // find the ocr text box containing the touched point or return null
                        imageX >= detected.box.x1 && imageX <= detected.box.x2 && imageY >= detected.box.y1 && imageY <= detected.box.y2
                    }

                    if (touchedText != null) {
                        TTSManager.speak(touchedText.text)
                    }
                }
            }

            true
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        TTSManager.shutdown()
    }

    private fun parseOcrText(json: String?): OCRResult {
        if (json.isNullOrBlank()) return OCRResult(emptyList())

        return try {
            val root = JSONObject(json)
            val arr = root.getJSONArray("detectedTexts")

            val detectedTexts = mutableListOf<DetectedText>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val boxObj = obj.getJSONObject("box")

                val box = BoundingBox(
                    x1 = boxObj.getInt("x1"),
                    y1 = boxObj.getInt("y1"),
                    x2 = boxObj.getInt("x2"),
                    y2 = boxObj.getInt("y2")
                )

                val detectedText = DetectedText(
                    text = obj.getString("text"),
                    box = box,
                    confidence = obj.optDouble("confidence", 1.0).toFloat()
                )

                detectedTexts.add(detectedText)
            }

            OCRResult(detectedTexts)
        } catch (e: Exception) {
            OCRResult(emptyList())
        }
    }

    private fun mapTouchToImagePoint(imageView: ImageView, touchX:Float, touchY:Float): Pair<Float, Float>? {
        val drawable = imageView.drawable ?: return null

        val inverseMatrix = android.graphics.Matrix()
        imageView.imageMatrix.invert(inverseMatrix)

        val points = floatArrayOf(touchX, touchY)
        inverseMatrix.mapPoints(points)

        val imageX = points[0]
        val imageY = points[1]

        if (
            imageX < 0 || imageY < 0 || imageX > drawable.intrinsicWidth || imageY > drawable.intrinsicHeight
        ) { return null }

        return Pair(imageX, imageY)
    }

}



