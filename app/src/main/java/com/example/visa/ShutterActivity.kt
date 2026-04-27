package com.example.visa

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ShutterActivity : AppCompatActivity() {

    private lateinit var resultImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_shutter)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.shutter)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        resultImageView = findViewById(R.id.resultImageView)
        val imagePath = intent.getStringExtra("imagePath")

        if (imagePath != null) {
            resultImageView.setImageBitmap(BitmapFactory.decodeFile(imagePath))
        }

        val btnGallery = findViewById<ImageView>(R.id.btnGallery)
        findViewById<android.view.View>(R.id.topBackBar).setOnClickListener {
            finish()
        }

    }
}