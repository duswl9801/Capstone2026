package com.example.visa

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.cardview.widget.CardView
import com.example.visa.accessibility.ScreenAccessibilityService

import com.example.visa.util.DialogUtils

class MainActivity : AppCompatActivity() {

    private lateinit var cardScreen: CardView
    private lateinit var txtScreen: TextView

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

        cardScreen = findViewById<CardView>(R.id.cardScreen)
        txtScreen = findViewById<TextView>(R.id.txtScreen)
        val cardCamera = findViewById<CardView>(R.id.cardCamera)
        val cardExit = findViewById<CardView>(R.id.cardExit)

        val tempText = findViewById<TextView>(R.id.temptextWould)

        cardCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        cardScreen.setOnClickListener {
            val intent = Intent(this, ScreenActivity::class.java)
            startActivity(intent)

        }

        cardExit.setOnClickListener {
            //finishAffinity()
            Toast.makeText(this, "Exit button clicked!", Toast.LENGTH_SHORT).show()
        }

        tempText.setOnClickListener {

            if (!DialogUtils.isAccessibilityServiceEnabled(this)) {
                Toast.makeText(this, "Please enable Screen Assistant first", Toast.LENGTH_SHORT).show()
                DialogUtils.showAccessibilityGuideDialog(this)
                return@setOnClickListener
            }

            val service = ScreenAccessibilityService.instance

            if (service == null) {
                Toast.makeText(this, "Accessibility service is not connected yet", Toast.LENGTH_SHORT).show()
                android.util.Log.d("ActionTest", "Service instance is null")
                return@setOnClickListener
            }

            // Temporary test: click Exit app
            val success = service.tempclickText("Exit app")

            android.util.Log.d("ActionTest", "Auto click Exit result: $success")

            Toast.makeText(
                this,
                if (success) "Clicked Exit" else "Exit button not found",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    override fun onResume() {
        super.onResume()
        updateScreenCardState()
    }

    private fun updateScreenCardState() {
        val isEnabled = DialogUtils.isAccessibilityServiceEnabled(this)
        if (isEnabled) {
            cardScreen.alpha = 1.0f
            txtScreen.text = "Photos, documents, apps"
        } else {
            cardScreen.alpha = 0.45f
            txtScreen.text = "Enable Screen Assistant first"
        }
    }

}