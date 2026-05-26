package com.example.visa.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import com.example.visa.dataclasses.RecommendedAction
import com.example.visa.dataclasses.UIElement

// Android creates and manages the service instance
// needed to read screen or run next action
class ScreenAccessibilityService : AccessibilityService() {

    // companion object: static member holder
    companion object { var instance: ScreenAccessibilityService? = null } // ScreenAccessibilityService.instance now can be accessed from anywhere

    override fun onServiceConnected() { // service is connected / enabled
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { // something happened on the screen after the service is running
        // intentionally empty
        // we read the screen only when the assistant bubble is clicked
    }

    override fun onInterrupt() {
        Log.d("ScreenService", "Accessibility service interrupted")
    }

    fun getCurrentUIElements(): List<UIElement> {
        val rootNode = rootInActiveWindow ?: run {
            Log.d("ScreenService", "rootInActiveWindow is null")
            return emptyList()
        }

        val elements = mutableListOf<UIElement>()
        collectUIElements(rootNode, elements)

        Log.d("ScreenService", "Collected UIElements size: ${elements.size}")
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

        // only include useful elements
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

    // function which runs next action based on vlm result
    fun executeNextAction(result: RecommendedAction): Boolean {
        val root = rootInActiveWindow ?: return false

        return when (result.action) {
            "ACTION_CLICK" -> {
                val targetNode = findTargetNode(root, result) ?: return false
                val clickableNode = findClickableParent(targetNode) ?: return false

                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            "ACTION_SET_TEXT" -> {
                val text = result.inputText ?: return false

                val targetNode = findTargetNode(root, result) ?: return false
                val editableNode = findEditableNode(targetNode) ?: return false

                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                }

                editableNode.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    args
                )
            }

            "ACTION_SCROLL_UP" -> {
                val rect = performScroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, result)
                rect != null
            }
            "ACTION_SCROLL_DOWN" -> {
                val rect = performScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, result)
                rect != null
            }
            "ACTION_SCROLL_LEFT" -> {
                val rect = performScroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, result)
                rect != null
            }
            "ACTION_SCROLL_RIGHT" -> {
                val rect = performScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, result)
                rect != null
            }

            "GLOBAL_ACTION_BACK" -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            else -> false
        }
    }

    private fun findTargetNode(node: AccessibilityNodeInfo?, result: RecommendedAction): AccessibilityNodeInfo? {
        val exactMatches = mutableListOf<AccessibilityNodeInfo>()
        val containsMatches = mutableListOf<AccessibilityNodeInfo>()

        fun norm(s: String?): String {
            return s
                ?.replace("\u2068", "")
                ?.replace("\u2069", "")
                ?.trim()
                ?.lowercase()
                ?: ""
        }

        val targetText = norm(result.targetText)
        val targetDesc = norm(result.targetContentDescription)

        fun isExactMatch(nodeText: String, nodeDesc: String): Boolean {
            return (targetText.isNotBlank() && nodeText == targetText) ||
                    (targetDesc.isNotBlank() && nodeDesc == targetDesc)
        }

        fun isContainsMatch(nodeText: String, nodeDesc: String): Boolean {
            return (targetText.isNotBlank() && nodeText.contains(targetText)) ||
                    (targetText.isNotBlank() && targetText.contains(nodeText) && nodeText.isNotBlank()) ||
                    (targetDesc.isNotBlank() && nodeDesc.contains(targetDesc)) ||
                    (targetDesc.isNotBlank() && targetDesc.contains(nodeDesc) && nodeDesc.isNotBlank())
        }

        fun search(current: AccessibilityNodeInfo?) {
            if (current == null) return

            val nodeText = norm(current.text?.toString())
            val nodeDesc = norm(current.contentDescription?.toString())

            if (isExactMatch(nodeText, nodeDesc)) {
                exactMatches.add(current)
            } else if (isContainsMatch(nodeText, nodeDesc)) {
                containsMatches.add(current)
            }

            for (i in 0 until current.childCount) {search(current.getChild(i))}
        }

        search(node)

        val matches = if (exactMatches.isNotEmpty()) exactMatches else containsMatches

        return when {
            matches.isEmpty() -> {
                Log.d("ScreenService",
                    "Target not found. targetText=${result.targetText}, targetDesc=${result.targetContentDescription}, targetClass=${result.targetClassName}")
                null
            }

            matches.size > 1 -> { // multiple elements are found
                Log.d("ScreenService", "Target is ambiguous: ${matches.size} matches found")
                matches.first()
            }

            else -> matches.first()
        }
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

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.isEditable) return node

        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i))
            if (found != null) return found
        }

        return null
    }

    private fun performScroll(
        action: Int,
        result: RecommendedAction? = null,
        maxScrolls: Int = 5
    ): Rect? {
        repeat(maxScrolls + 1) {attempt ->
            val currentRoot = rootInActiveWindow ?: return null

            // if target info exists, check if target is already visible
            if (result != null) {
                val targetNode = findTargetNode(currentRoot, result)

                if (targetNode != null) {
                    Log.d("ScreenService", "Target found after scroll attempt $attempt")

                    val rect = Rect()
                    targetNode.getBoundsInScreen(rect)
                    return rect
                }
            }

            // if this is last attempt, stop
            if (attempt == maxScrolls) return null

            val queue = ArrayDeque<AccessibilityNodeInfo>() // create a queue to search through ui nodes
            queue.add(currentRoot)

            var scrolled = false // track whether scrolling succeeded

            while (queue.isNotEmpty()) {
                val node = queue.removeFirst() // get the next node

                // if this node can scroll, perform the scroll action
                if (node.isScrollable) {
                    scrolled = node.performAction(action)
                    break // stop after one scroll action
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }

            if (!scrolled) return null // if no scrollable node was found, stop

            // wait for screen update after scroll
            Thread.sleep(300)
        }
        return null
    }

    fun giveTargetInfo(result: RecommendedAction): UIElement? {
        val root = rootInActiveWindow ?: return null

        val targetNode = findTargetNode(root, result) ?: return null

        val rect = Rect()
        targetNode.getBoundsInScreen(rect)

        return UIElement(
            text = targetNode.text?.toString(),
            contentDescription = targetNode.contentDescription?.toString(),
            className = targetNode.className?.toString(),
            packageName = targetNode.packageName?.toString(),
            clickable = targetNode.isClickable,
            editable = targetNode.isEditable,
            bounds = rect.toShortString()
        )
    }

}