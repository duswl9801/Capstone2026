package com.example.visa.util

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.view.View
import android.widget.TextView
import com.example.visa.accessibility.ScreenAccessibilityService
import com.example.visa.dataclasses.BoundingBox
import java.io.ByteArrayOutputStream
import android.util.Base64

object Utils {

    // check if accessibility is turned on
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = ComponentName(
            context,
            ScreenAccessibilityService::class.java
        ).flattenToString()

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            it.equals(expectedServiceName, ignoreCase = true)
        }
    }

    fun Context.dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    fun Context.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    fun parseBoundsToBoundingBox(bounds: String?): BoundingBox? {
        if (bounds.isNullOrBlank()) return null

        val regex = Regex("""\[(\-?\d+),(\-?\d+)\]\[(\-?\d+),(\-?\d+)\]""")
        val match = regex.find(bounds) ?: return null

        return BoundingBox(
            x1 = match.groupValues[1].toInt(),
            y1 = match.groupValues[2].toInt(),
            x2 = match.groupValues[3].toInt(),
            y2 = match.groupValues[4].toInt()
        )
    }

    fun showLoading(loadingOverlay: View, loadingText: TextView, message: String = "Please wait...") {
        loadingText.text = message
        loadingOverlay.visibility = View.VISIBLE
    }

    fun hideLoading(loadingOverlay: View) {
        loadingOverlay.visibility = View.GONE
    }

    fun bitmapToBase64(img: Bitmap): String {
        val outputStream = ByteArrayOutputStream()

        img.compress(
            Bitmap.CompressFormat.JPEG,
            85,
            outputStream
        )

        val imageBytes = outputStream.toByteArray()

        return Base64.encodeToString(
            imageBytes,
            Base64.NO_WRAP
        )
    }

}