package com.example.visa

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch


import android.util.Log
import android.graphics.Bitmap
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.visa.analyzer.VisualAnalyzer

class MainActivity : AppCompatActivity() {

    private val TAG = "VisualAnalyzerTest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        AppContainer.init(this)

        val cardScreen = findViewById<CardView>(R.id.cardScreen)
        val cardCamera = findViewById<CardView>(R.id.cardCamera)
        val cardExit = findViewById<CardView>(R.id.cardExit)

        cardCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }


        ////////////////////////////////////////////////
        val analyzer = VisualAnalyzer(
            ocrName = "EasyOCR",
            confidenceThreshold = 0.5f,
            mergeDistanceThreshhold_x = 20,
            mergeDistanceThreshhold_y = 20,
            modelName = "Moondream"
        )

        val dummyBitmap = Bitmap.createBitmap(500, 800, Bitmap.Config.ARGB_8888)

        lifecycleScope.launch {
            val result = analyzer.detectText(dummyBitmap)

            result.detectedTexts.forEach {
                Log.d(TAG, "text=${it.text}, confidence=${it.confidence}, box=${it.box}")
            }
        }
        ///////////////////////////////////////////////

    }
}