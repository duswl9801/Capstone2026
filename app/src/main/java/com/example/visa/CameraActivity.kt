package com.example.visa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas

import com.example.visa.analyzer.VisualAnalyzer
import com.example.visa.util.BoxMapper
import com.example.visa.utils.BitmapUtils

class CameraActivity : AppCompatActivity() {

    private lateinit var analyzer: VisualAnalyzer

    private lateinit var viewPreview: PreviewView
    private lateinit var highlightOverlayView: HighlightOverlayView
    private var imageCapture: ImageCapture? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        analyzer = AppContainer.visualAnalyzer
        setContentView(R.layout.activity_camera)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.camera)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewPreview = findViewById(R.id.viewPreview)
        highlightOverlayView = findViewById(R.id.highlightOverlay)

        val btnShutter = findViewById<ImageView>(R.id.btnShutter)
        val btnGallery = findViewById<ImageView>(R.id.btnGallery)

        findViewById<android.view.View>(R.id.topBackBar).setOnClickListener {
            finish()
        }

        btnShutter.setOnClickListener {
            captureImage()
        }

        btnGallery.setOnClickListener {
            // TODO: open gallery
        }

        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        val permission = Manifest.permission.CAMERA

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(permission)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewPreview.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }


    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val photoFile = File( // save cache
            cacheDir,
            "capture_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val rawBitmap  = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val bitmap = BitmapUtils.rotateBitmapIfNeeded(rawBitmap, photoFile.absolutePath)

                    // run OCR with coroutine function
                    lifecycleScope.launch {
                        val result = analyzer.detectText(bitmap)
                        val boxes = result.detectedTexts.map { it.box }

                        // create bitmap same size as captured bitmap
                        val highlightedBitmap = Bitmap.createBitmap(
                            bitmap.width,
                            bitmap.height,
                            Bitmap.Config.ARGB_8888
                        )

                        val canvas = Canvas(highlightedBitmap)
                        canvas.drawBitmap(bitmap, 0f, 0f, null) // original image
                        highlightOverlayView.setBoxes(boxes) // add ocr boxes
                        highlightOverlayView.layout(
                            0,
                            0,
                            bitmap.width,
                            bitmap.height
                        )

                        // draw boxes on the canvas
                        highlightOverlayView.drawOverlay(canvas)

                        // save
                        val highlightedImagePath = BitmapUtils.saveBitmapToCache(
                            context = this@CameraActivity,
                            bitmap = highlightedBitmap
                        )

                        // convey to shutter activity
                        val intent = Intent(this@CameraActivity, ShutterActivity::class.java).apply {
                            putExtra("imagePath", highlightedImagePath)
                        }

                        startActivity(intent)
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraActivity", "Image capture failed", exception)
                }
            }
        )

    }

}