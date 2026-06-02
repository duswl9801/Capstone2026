package com.example.visa

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class SettingActivity : BaseActivity() {

    private lateinit var cardLanguage: View
    private lateinit var cardVoiceMode: View
    private lateinit var cardTheme: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_setting)

        val main = findViewById<View>(R.id.setting)

        val originalLeft = main.paddingLeft
        val originalTop = main.paddingTop
        val originalRight = main.paddingRight
        val originalBottom = main.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setting)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                originalLeft + systemBars.left,
                originalTop + systemBars.top,
                originalRight + systemBars.right,
                originalBottom + systemBars.bottom
            )
            insets
        }

        findViewById<View>(R.id.topBackBar).setOnClickListener {
            finish()
        }

        cardLanguage = findViewById(R.id.cardLanguage)
        cardVoiceMode = findViewById(R.id.cardVoiceMode)
        cardTheme = findViewById(R.id.cardTheme)

        cardLanguage.setOnClickListener {
            // Eng - Korean
            val currentLang = AppCompatDelegate
                .getApplicationLocales()
                .get(0)
                ?.language ?: "en"

            val nextLang = if (currentLang == "ko") "en" else "ko"

            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(nextLang)
            )
        }

        cardVoiceMode.setOnClickListener {
            // later: open voice mode settings
        }

        cardTheme.setOnClickListener {
            // later: open theme settings
            toggleTheme()
        }
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentTheme = prefs.getString("theme", "default")

        val nextTheme = if (currentTheme == "crazy") {
            "default"
        } else {
            "crazy"
        }

        prefs.edit()
            .putString("theme", nextTheme)
            .apply()

        recreate()
    }

}