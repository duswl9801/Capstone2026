package com.example.visa

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.cardview.widget.CardView

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

        cardCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        cardScreen.setOnClickListener {
            DialogUtils.handleScreenAssistantClick(this){
                // execute the function immediately when the service is already enabled
                moveTaskToBack(true)
            }
        }

        cardExit.setOnClickListener {
            finishAffinity()
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