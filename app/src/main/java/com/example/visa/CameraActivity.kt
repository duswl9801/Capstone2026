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
import android.graphics.Canvas
import android.view.View
import com.example.visa.utils.BitmapUtils
import androidx.core.graphics.createBitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.TextView

import com.example.visa.analyzer.VisualAnalyzer
import com.example.visa.overlay.HighlightOverlayView
import com.example.visa.util.JsonUtils
import com.example.visa.util.Utils

class CameraActivity : BaseActivity() {

    private lateinit var analyzer: VisualAnalyzer

    private lateinit var viewPreview: PreviewView
    private lateinit var highlightOverlayView: HighlightOverlayView
    private lateinit var btnShutter: ImageView
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView

    private var capturedImage: ImageCapture? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { startCamera() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_camera)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.camera)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        analyzer = AppContainer.visualAnalyzer

        viewPreview = findViewById(R.id.viewPreview)
        highlightOverlayView = findViewById(R.id.highlightOverlay)

        btnShutter = findViewById<ImageView>(R.id.btnShutter)
        val btnGallery = findViewById<ImageView>(R.id.btnGallery)
        btnGallery.visibility = View.GONE // make it visible when the function implemented

        // loading
        loadingOverlay = findViewById(R.id.loadingOverlayContainer)
        loadingText = loadingOverlay.findViewById(R.id.loadingText)

        findViewById<View>(R.id.topBackBar).setOnClickListener {
            finish()
        }

        btnShutter.setOnClickListener {
            btnShutter.isEnabled = false

            vibrateOnCapture()
            showCameraFlash()

            captureImage()
        }

        btnGallery.setOnClickListener {
            // TODO: open gallery
        }

        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        val permission = Manifest.permission.CAMERA

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) { startCamera() }
        else { cameraPermissionLauncher.launch(permission) }
    }

    private fun captureImage() {
        val capturedImage = capturedImage ?: return

        // save cache
        val photoFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build() // info where to save

        // take a photo and process
        capturedImage.takePicture( // take a photo with CameraX
            outputOptions,
            ContextCompat.getMainExecutor(this), // run callback on the main thread
            object : ImageCapture.OnImageSavedCallback { // callback object running after saving image

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    btnShutter.isEnabled = true

                    Utils.showLoading(loadingOverlay, loadingText, "Detecting texts...")

                    val rawBitmap  = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val bitmap = BitmapUtils.rotateBitmapIfNeeded(rawBitmap, photoFile.absolutePath) // bitmap rotation calibrate if needed

                    // run OCR with coroutine function
                    lifecycleScope.launch {
                        val result = analyzer.detectText(bitmap)
                        //val result = analyzer.detectText_ocr(bitmap)

                        // pack OCR text strings into a JSON array to pass to ShutterActivity
                        val ocrJson = JsonUtils.ocrResultToJson(result).toString()
                        // map: create a new list by transforming each item in the original list into another value
                        val boxes = result.detectedTexts.map { it.box }

                        // create bitmap same size as captured bitmap
                        val highlightedBitmap = createBitmap(bitmap.width, bitmap.height)
                        val canvas = Canvas(highlightedBitmap)

                        canvas.drawBitmap(bitmap, 0f, 0f, null) // original image
                        highlightOverlayView.setBoxes(boxes) // add ocr boxes
                        highlightOverlayView.layout(0,0,bitmap.width,bitmap.height)
                        // draw boxes on the canvas
                        highlightOverlayView.drawOverlay(canvas)

                        // save
                        val highlightedImagePath = BitmapUtils.saveBitmapToCache(
                            context = this@CameraActivity,
                            bitmap = highlightedBitmap
                        )

                        // convey to shutter activity
                        val intent = Intent(this@CameraActivity, ShutterActivity::class.java).apply {
                            putExtra("ocrText", ocrJson)
                            putExtra("imagePath", highlightedImagePath)
                        }

                        highlightOverlayView.setBoxes(emptyList()) // erase highlights
                        Utils.hideLoading(loadingOverlay)

                        startActivity(intent)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    btnShutter.isEnabled = true
                    Log.e("CameraActivity", "Image capture failed", exception)
                }
            }
        )

    }

    // show camera preview
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewPreview.surfaceProvider)
            }

            capturedImage = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                capturedImage
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun vibrateOnCapture() {
        val duration = 40L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VibratorManager::class.java)
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        }

    }

    private fun showCameraFlash() {
        val flashView = findViewById<View>(R.id.cameraFlashView)

        flashView.visibility = View.VISIBLE
        flashView.alpha = 0f

        flashView.animate()
            .alpha(0.85f)
            .setDuration(60)
            .withEndAction {
                flashView.animate()
                    .alpha(0f)
                    .setDuration(180)
                    .withEndAction {
                        flashView.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }

}