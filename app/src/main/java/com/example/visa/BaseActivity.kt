package com.example.visa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val themeName = prefs.getString("theme", "default")

        when (themeName) {
            "crazy" -> setTheme(R.style.Theme_VisA_Crazy)
            else -> setTheme(R.style.Theme_VisA)
        }
    }

}