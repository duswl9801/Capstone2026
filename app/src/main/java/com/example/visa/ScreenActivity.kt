package com.example.visa

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.provider.Settings


import com.example.visa.accessibility.ScreenAccessibilityService
import com.example.visa.analyzer.VisualAnalyzer
import com.example.visa.dataclasses.OCRResult
import com.example.visa.dataclasses.ScreenContext
import com.example.visa.overlay.ScreenOverlayService
import com.example.visa.util.DialogUtils


class ScreenActivity : AppCompatActivity() {

    private lateinit var screenService: ScreenAccessibilityService
    private lateinit var analyzer: VisualAnalyzer
    private lateinit var screenContext: ScreenContext

    private var firstWords = "How can I help you?"

    private var hasStartedOverlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        analyzer = AppContainer.visualAnalyzer
        setContentView(R.layout.activity_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.screen)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        """
            var textResults = findViewById<TextView>(R.id.textResults)
            val mockScreenContext = ScreenContext (
            uies = emptyList(),
            texts = OCRResult(emptyList()),
            userGoal= "refill my prescription",
            screenSummary = "walgreens homepage"
        )

        lifecycleScope.launch {
            try {
                val result = analyzer.getNextAction(mockScreenContext)
                textResults.setText(result)
                android.util.Log.d("ActionServer", result)
            } catch (e: Exception) {
                android.util.Log.e("ActionServer", "Error: ${'$'}{e.message}")
            }
        }
        """.trimIndent()
    }

    override fun onResume() {
        super.onResume()
        if (hasStartedOverlay) return

        // check accessibility permission
        if (!DialogUtils.isAccessibilityServiceEnabled(this)) {
            DialogUtils.showAccessibilityGuideDialog(this)
            return
        }

        // check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        hasStartedOverlay = true
        startService(Intent(this, ScreenOverlayService::class.java))
        moveTaskToBack(true)
    }


}