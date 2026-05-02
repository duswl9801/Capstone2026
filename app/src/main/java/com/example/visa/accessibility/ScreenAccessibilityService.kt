package com.example.visa.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return

        Log.d("ScreenService", "===== Current Screen UI Tree =====")
        traverseNode(rootNode)
    }

    override fun onInterrupt() {
        Log.d("ScreenService", "Accessibility service interrupted")
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null) return

        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val className = node.className?.toString()
        val packageName = node.packageName?.toString()

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val hasUsefulText = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()

        if (hasUsefulText) {
            Log.d(
                "ScreenService",
                """
                Text: $text
                ContentDesc: $contentDesc
                Class: $className
                Package: $packageName
                Clickable: ${node.isClickable}
                Editable: ${node.isEditable}
                Bounds: $rect
                """.trimIndent()
            )
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), depth + 1)
        }
    }

}