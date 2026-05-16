package com.example.visa

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.example.visa.util.TTSManager
import org.json.JSONArray

class ShutterActivity : AppCompatActivity() {

    private lateinit var resultImageView: ImageView
    private lateinit var btnSpeak: ImageView

    // Flat string built from the OCR JSON array, ready to be spoken
    private var ocrReadText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_shutter)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.shutter)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Init TTS
        TTSManager.init(this)

        resultImageView = findViewById(R.id.resultImageView)
        btnSpeak        = findViewById(R.id.btnSpeak)

        // load image
        val imagePath = intent.getStringExtra("imagePath")
        if (imagePath != null) {
            resultImageView.setImageBitmap(BitmapFactory.decodeFile(imagePath))
        }

        // Parse OCR text from JSON array extra
        val ocrJson = intent.getStringExtra("ocrText")
        ocrReadText = parseOcrText(ocrJson)

        val btnGallery = findViewById<ImageView>(R.id.btnGallery)
        findViewById<android.view.View>(R.id.topBackBar).setOnClickListener {
            finish()
        }

        // Speaker button — tap to speak, tap again to stop
        btnSpeak.setOnClickListener {
            if (ocrReadText.isBlank()) {
                TTSManager.speak("No text was detected in this image.")
            } else {
                TTSManager.speak(ocrReadText)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TTSManager.shutdown()
    }

    /**
     * Converts a JSON array string like ["Hello","World"] into
     * a single readable sentence: "Hello. World."
     * Returns empty string if parsing fails or input is null.
     */
    private fun parseOcrText(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try {
            val arr = JSONArray(json)
            buildString {
                for (i in 0 until arr.length()) {
                    val segment = arr.getString(i).trim()
                    if (segment.isNotBlank()) {
                        append(segment)
                        if (!segment.endsWith(".")) append(".")
                        append(" ")
                    }
                }
            }.trim()
        } catch (e: Exception) {
            ""
        }
    }

}



