package com.example.visa.util

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import com.example.visa.R
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import com.example.visa.accessibility.ScreenAccessibilityService

object DialogUtils {

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

    // accessibility dialog
    fun showAccessibilityGuideDialog(context: Context) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_screen_assistant_guide, null)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        val btnOpenSettings = view.findViewById<Button>(R.id.btnOpenSettings)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        btnOpenSettings.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

    }

    fun handleScreenAssistantClick(context: Context, onEnabled: () -> Unit) {
        if (isAccessibilityServiceEnabled(context)) { onEnabled() } else {
            showAccessibilityGuideDialog(context)
        }
    }

}