package com.example.visa

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.cardview.widget.CardView
import com.example.visa.overlay.ScreenOverlayService
import android.provider.Settings
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.example.visa.dataclasses.AssistMode
import com.example.visa.dataclasses.Assistant

import com.example.visa.util.DialogUtils
import com.example.visa.util.Utils

class MainActivity : BaseActivity() {

    private lateinit var assistant: Assistant
    private lateinit var cardScreen: CardView
    private lateinit var txtScreen: TextView

    private var currentThemeName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val main = findViewById<View>(R.id.main)

        currentThemeName = getSavedThemeName()

        val originalLeft = main.paddingLeft
        val originalTop = main.paddingTop
        val originalRight = main.paddingRight
        val originalBottom = main.paddingBottom


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                originalLeft + systemBars.left,
                originalTop + systemBars.top,
                originalRight + systemBars.right,
                originalBottom + systemBars.bottom
            )
            insets
        }

        // init AppContainer so that I can use over activities
        AppContainer.init(this)

        assistant = AppContainer.assistant

        cardScreen = findViewById<CardView>(R.id.cardScreen)
        txtScreen = findViewById<TextView>(R.id.txtScreen)
        val cardCamera = findViewById<CardView>(R.id.cardCamera)
        val cardExit = findViewById<CardView>(R.id.cardExit)
        val btnHelp = findViewById<ImageView>(R.id.btnHelp)
        val btnSetting = findViewById<ImageView>(R.id.btnSetting)

        cardCamera.setOnClickListener {
            assistant.start(AssistMode.CAMERA)

            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        cardScreen.setOnClickListener {
            handlePermission()
        }

        btnHelp.setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
        }

        btnSetting.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        }

        cardExit.setOnClickListener {
            stopService(Intent(this, ScreenOverlayService::class.java))
            finishAffinity()
        }

    }

    override fun onResume() {
        super.onResume()

        val savedThemeName = getSavedThemeName()

        if (currentThemeName != null && currentThemeName != savedThemeName) {
            currentThemeName = savedThemeName
            recreate()
            return
        }

        updateScreenCardState()
    }

    private fun handlePermission() {
        val isAccessibilityEnabled = Utils.isAccessibilityServiceEnabled(this)

        // to access to UI elements and run some actions
        if (!isAccessibilityEnabled) {
            DialogUtils.showAccessibilityGuideDialog(this)
            return
        }

        // to draw on the other screens
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        startScreenAssistant()

    }

    private fun startScreenAssistant() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE) // call SharedPreferences file named "app_prefs", make a new one if no exist
        val seenGuide = prefs.getBoolean("seenGuide", false) // call "firstUsage" value from prefs, set false if no exist

        assistant.start(AssistMode.SCREEN)

        if (!seenGuide) {
            // TODO: assistant speak guidance?
            startActivity(Intent(this, GuideActivity::class.java))
            return
        } else {
            startService(Intent(this, ScreenOverlayService::class.java))
            // send this app to the background so the user can open another app
            moveTaskToBack(true)
        }

    }

    private fun updateScreenCardState() {
        val isAccessibilityEnabled = Utils.isAccessibilityServiceEnabled(this)
        if (isAccessibilityEnabled) {
            cardScreen.alpha = 1.0f
            txtScreen.text = "Photos, documents, apps"
        } else {
            cardScreen.alpha = 0.45f
            txtScreen.text = "Enable Screen Assistant first"
        }
    }

    private fun getSavedThemeName(): String {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getString("theme", "default") ?: "default"
    }

}