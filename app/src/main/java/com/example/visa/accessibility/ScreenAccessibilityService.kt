package com.example.visa.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import com.example.visa.dataclasses.BoundingBox
import com.example.visa.dataclasses.UIElement
import com.example.visa.dataclasses.UIElementType

// read screen or do next action
class ScreenAccessibilityService : AccessibilityService() {

    // current active service instance
    // used so MainActivity can access this AccessibilityService
    // because Android creates and manages the service instance
    companion object {
        var instance: ScreenAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Do nothing for now.
        // UI elements are collected only when the assistant bubble is clicked.
    }

    override fun onInterrupt() {
        Log.d("ScreenService", "Accessibility service interrupted")
    }

    fun tempclickText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false

        val nodes = root.findAccessibilityNodeInfosByText(targetText)

        for (node in nodes) {
            val clickableNode = findClickableParent(node)
            if (clickableNode != null) {
                val result = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                android.util.Log.d("ScreenService", "Click result: $result")
                return result
            }
        }
        android.util.Log.d("ScreenService", "Target not found: $targetText")
        return false
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node

        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }

        return null
    }

    fun getCurrentUIElements(): List<UIElement> {
        val rootNode = rootInActiveWindow ?: run {
            Log.d("ScreenService", "rootInActiveWindow is null")
            return emptyList()
        }

        val elements = mutableListOf<UIElement>()
        collectUIElements(rootNode, elements)

        Log.d("ScreenService", "Collected UIElements: ${elements.size}")
        return elements

    }

    private fun collectUIElements(node:AccessibilityNodeInfo?, elements: MutableList<UIElement>) {
        if (node == null) return

        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val className = node.className?.toString()
        val packageName = node.packageName?.toString()

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val hasUsefulText = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()
        val isUsefulActionTarget = node.isClickable || node.isEditable

        if (hasUsefulText || isUsefulActionTarget) {
            elements.add(
                UIElement(
                    text = text,
                    contentDescription = contentDesc,
                    className = className,
                    packageName = packageName,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    bounds = rect.toShortString()
                )
            )
        }

        for (i in 0 until node.childCount) {
            collectUIElements(node.getChild(i), elements)
        }
    }

}