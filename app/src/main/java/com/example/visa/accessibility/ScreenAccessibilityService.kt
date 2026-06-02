package com.example.visa.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import android.graphics.Path

import com.example.visa.dataclasses.RecommendedAction
import com.example.visa.dataclasses.ActionCandidate
import com.example.visa.dataclasses.UIElement
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

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

    private fun collectUIElements(node: AccessibilityNodeInfo?, elements: MutableList<UIElement>) {
        if (node == null) return

        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val className = node.className?.toString()
        val packageName = node.packageName?.toString()
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (!isValidBounds(rect)) return

        val isActionTarget = node.isClickable || node.isEditable

        // check if this node contains another clickable/editable child
        // if yes, do not group this parent. Let child action targets be collected separately
        //var hasActionableChild = false
        //for (i in 0 until node.childCount) {
        //    val child = node.getChild(i)

        //    if (child != null && (child.isClickable || child.isEditable)) {
        //        hasActionableChild = true
        //        break
        //    }
        //}

        if (isActionTarget) {
            val childText = collectTextFromSubtree(node)

            elements.add(
                UIElement(
                    text = childText.ifBlank  { text ?: "" },
                    contentDescription = contentDesc ?: "",
                    className = className,
                    packageName = packageName,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    bounds = rect.toShortString()
                )
            )

            // child TextViews are merged into the clickable/editable parent, so skip them here
            // return
            // do not return here.
            // even if the parent is clickable, it may contain an editable child.
        }

        // hasUsefulText = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()
        //if (hasUsefulText) {
        //    elements.add(
        //        UIElement(
        //            text = text,
        //            contentDescription = contentDesc,
        //            className = className,
        //            packageName = packageName,
        //            clickable = false,
        //            editable = false,
        //            bounds = rect.toShortString()
        //        )
        //    )
        //}

        for (i in 0 until node.childCount) {
            collectUIElements(node.getChild(i), elements)
        }
    }

    private fun collectTextFromSubtree(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val texts = mutableListOf<String>()

        fun dfs(n: AccessibilityNodeInfo?) {
            if (n == null) return

            var t = n.text?.toString()?.trim() ?: ""
            var cd = n.contentDescription?.toString()?.trim() ?: ""

            // simple normalize: collapse multiple spaces/newlines into one space
            t = t.replace(Regex("\\s+"), " ")
            cd = cd.replace(Regex("\\s+"), " ")

            // limit each child text length 60 for each
            if (t.length > 60) {
                t = t.take(60) + "..."
            }

            if (cd.length > 60) {
                cd = cd.take(60) + "..."
            }

            if (t.isNotBlank()) texts.add(t)
            if (cd.isNotBlank() && cd != t) texts.add(cd)

            for (i in 0 until n.childCount) {
                dfs(n.getChild(i))
            }
        }

        dfs(node)

        return texts
            .distinct()
            .joinToString(" | ")
            .take(250) // total text limit 250
    }

    private fun isValidBounds(rect: Rect): Boolean {
        if (rect.width() <= 2 || rect.height() <= 2) return false

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        if (rect.right <= 0 || rect.left >= screenWidth) return false
        if (rect.bottom <= 0 || rect.top >= screenHeight + 300) return false

        return true
    }

    private fun collectActionCandidates(root: AccessibilityNodeInfo?): List<ActionCandidate> {
        if (root == null) return emptyList()

        val candidates = mutableListOf<ActionCandidate>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (!isValidBounds(rect)) return

            val isActionable = node.isClickable || node.isEditable || node.isScrollable

            if (isActionable) {
                val ownText = node.text?.toString()?.trim().orEmpty()
                val groupedText = collectTextFromSubtree(node).trim()
                val finalText = groupedText.ifBlank { ownText }

                candidates.add(
                    ActionCandidate(
                        node = node,
                        text = finalText,
                        contentDescription = node.contentDescription?.toString().orEmpty(),
                        className = node.className?.toString().orEmpty(),
                        packageName = node.packageName?.toString().orEmpty(),
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        scrollable = node.isScrollable,
                        bounds = rect.toShortString(),
                        boundsRect = Rect(rect)
                    )
                )
            }

            for (i in 0 until node.childCount) {
                dfs(node.getChild(i))
            }
        }

        dfs(root)
        return candidates
    }

    private fun norm(s: String?): String {
        return s
            ?.replace("\u2068", "")
            ?.replace("\u2069", "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.lowercase()
            ?: ""
    }

    private fun findBestClickCandidate(
        root: AccessibilityNodeInfo?,
        result: RecommendedAction
    ): ActionCandidate? {
        val candidates = collectActionCandidates(root)
            .filter { it.clickable }

        val targetText = norm(result.targetText)
        val targetDesc = norm(result.targetContentDescription)
        val targetClass = norm(result.targetClassName)

        Log.d(
            "ClickAction",
            "CLICK target from server: targetText=${result.targetText}, targetDesc=${result.targetContentDescription}, targetClass=${result.targetClassName}"
        )

        data class ScoredCandidate(
            val candidate: ActionCandidate,
            val score: Int
        )

        val scored = candidates.mapNotNull { candidate ->
            val text = norm(candidate.text)
            val desc = norm(candidate.contentDescription)
            val className = norm(candidate.className)

            var score = 0
            var hasRealMatch = false

            // text is the main matching signal
            if (targetText.isNotBlank() && text.isNotBlank()) {
                when {
                    text == targetText -> {
                        score += 100
                        hasRealMatch = true
                    }
                    text.contains(targetText) -> {
                        score += 70
                        hasRealMatch = true
                    }
                    targetText.contains(text) -> {
                        score += 40
                        hasRealMatch = true
                    }
                }
            }

            // contentDescription is also a real matching signal
            if (targetDesc.isNotBlank() && desc.isNotBlank()) {
                when {
                    desc == targetDesc -> {
                        score += 100
                        hasRealMatch = true
                    }
                    desc.contains(targetDesc) -> {
                        score += 70
                        hasRealMatch = true
                    }
                    targetDesc.contains(desc) -> {
                        score += 40
                        hasRealMatch = true
                    }
                }
            }

            // className is only a bonus.
            // Do not allow className alone to make a candidate match.
            if (targetClass.isNotBlank() && className == targetClass) {
                score += 20
            }

            if (hasRealMatch) {
                ScoredCandidate(candidate, score)
            } else {
                null
            }
        }.sortedByDescending { it.score }

        scored.take(5).forEachIndexed { index, item ->
            Log.d(
                "ClickAction",
                "CLICK candidate[$index]: score=${item.score}, text=${item.candidate.text}, desc=${item.candidate.contentDescription}, class=${item.candidate.className}, clickable=${item.candidate.clickable}, bounds=${item.candidate.bounds}"
            )
        }

        return scored.firstOrNull()?.candidate
    }

    private fun findBestEditableCandidate(
        root: AccessibilityNodeInfo?,
        result: RecommendedAction
    ): ActionCandidate? {
        val candidates = collectActionCandidates(root)
            .filter { it.editable }

        val targetText = norm(result.targetText)
        val targetDesc = norm(result.targetContentDescription)
        val targetClass = norm(result.targetClassName)

        Log.d(
            "SetTextAction",
            "SET_TEXT target from server: targetText=${result.targetText}, targetDesc=${result.targetContentDescription}, targetClass=${result.targetClassName}, inputText=${result.inputText}"
        )

        data class ScoredCandidate(
            val candidate: ActionCandidate,
            val score: Int
        )

        val scored = candidates.mapNotNull { candidate ->
            val text = norm(candidate.text)
            val desc = norm(candidate.contentDescription)
            val className = norm(candidate.className)

            var score = 0

            if (targetText.isNotBlank() && text.isNotBlank()) {
                when {
                    text == targetText -> score += 100
                    text.contains(targetText) -> score += 70
                    targetText.contains(text) -> score += 40
                }
            }

            if (targetDesc.isNotBlank() && desc.isNotBlank()) {
                when {
                    desc == targetDesc -> score += 100
                    desc.contains(targetDesc) -> score += 70
                    targetDesc.contains(desc) -> score += 40
                }
            }

            // className is only a bonus, not a match by itself
            if (targetClass.isNotBlank() && className == targetClass) {
                score += 20
            }

            // if there is only one editable field, it is probably the target
            if (candidates.size == 1 && score == 0) {
                score += 30
            }

            if (score > 0) {
                ScoredCandidate(candidate, score)
            } else {
                null
            }
        }.sortedByDescending { it.score }

        scored.take(5).forEachIndexed { index, item ->
            Log.d(
                "SetTextAction",
                "SET_TEXT candidate[$index]: score=${item.score}, text=${item.candidate.text}, desc=${item.candidate.contentDescription}, class=${item.candidate.className}, editable=${item.candidate.editable}, bounds=${item.candidate.bounds}"
            )
        }

        return scored.firstOrNull()?.candidate
    }

    // function which runs next action based on vlm result
    fun executeNextAction(result: RecommendedAction): Boolean {
        val root = rootInActiveWindow ?: return false

        return when (result.action) {
            "ACTION_CLICK" -> {
                val candidate = findBestClickCandidate(root, result)

                if (candidate == null) {
                    Log.d(
                        "ClickAction",
                        "CLICK failed: no clickable candidate found. targetText=${result.targetText}, targetDesc=${result.targetContentDescription}, targetClass=${result.targetClassName}"
                    )
                    return false
                }

                Log.d(
                    "ClickAction",
                    "CLICK candidate: text=${candidate.text}, desc=${candidate.contentDescription}, class=${candidate.className}, clickable=${candidate.clickable}, editable=${candidate.editable}, bounds=${candidate.bounds}"
                )

                val success = tapRect(candidate.boundsRect)

                Log.d("ClickAction", "CLICK gesture dispatch result: $success")

                success
            }
            "ACTION_SET_TEXT" -> {
                val text = result.inputText

                if (text.isNullOrBlank()) {
                    Log.d("SetTextAction", "SET_TEXT failed: inputText is empty")
                    return false
                }

                // 1. first try direct editable field
                val directEditable = findBestEditableCandidate(root, result)

                if (directEditable != null) {
                    Log.d(
                        "SetTextAction",
                        "SET_TEXT direct editable: text=${directEditable.text}, class=${directEditable.className}, bounds=${directEditable.bounds}, inputText=$text"
                    )

                    return setTextToCandidate(directEditable, text, result)
                }

                Log.d("SetTextAction", "No editable candidate found. Trying to open input field first.")

                // 2. if no editable exists, tap the clickable target that opens the input field
                val openerCandidate = findBestClickCandidate(root, result)

                if (openerCandidate == null) {
                    Log.d(
                        "SetTextAction",
                        "SET_TEXT failed: no clickable opener found. targetText=${result.targetText}, targetDesc=${result.targetContentDescription}, targetClass=${result.targetClassName}"
                    )
                    return false
                }

                Log.d(
                    "SetTextAction",
                    "SET_TEXT opener candidate: text=${openerCandidate.text}, class=${openerCandidate.className}, bounds=${openerCandidate.bounds}"
                )

                val tapSuccess = tapRect(openerCandidate.boundsRect)

                Log.d("SetTextAction", "SET_TEXT opener tap result: $tapSuccess")

                if (!tapSuccess) return false

                // 3. wait until editable field appears after opener tap
                val finalEditable = waitForEditableCandidate(
                    result = result,
                    timeoutMs = 1200L,
                    intervalMs = 150L
                )

                if (finalEditable == null) {
                    Log.d("SetTextAction", "SET_TEXT failed: no editable found after opener tap")
                    return false
                }

                Log.d(
                    "SetTextAction",
                    "SET_TEXT final editable: text=${finalEditable.text}, class=${finalEditable.className}, bounds=${finalEditable.bounds}, inputText=$text"
                )

                setTextToCandidate(finalEditable, text, result)
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

    private fun tapRect(rect: Rect): Boolean {
        if (rect.isEmpty) return false

        val width = rect.width()
        val height = rect.height()

        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    80L
                )
            )
            .build()

        Log.d(
            "ClickAction",
            "Gesture tap at x=$x, y=$y, bounds=${rect.toShortString()}, width=$width, height=$height"
        )

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d("ClickAction", "Gesture tap completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.d("ClickAction", "Gesture tap cancelled")
                }
            },
            null
        )
    }

    private fun waitForEditableCandidate(
        result: RecommendedAction,
        timeoutMs: Long = 1200L,
        intervalMs: Long = 150L
    ): ActionCandidate? {
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeoutMs) {
            val currentRoot = rootInActiveWindow

            val candidates = collectActionCandidates(currentRoot).filter { it.editable }

            Log.d(
                "SetTextAction",
                "waiting editable: count=${candidates.size}"
            )

            candidates.forEachIndexed { index, candidate ->
                Log.d(
                    "SetTextAction",
                    "editable[$index]: focused=${candidate.node.isFocused}, text=${candidate.text}, desc=${candidate.contentDescription}, class=${candidate.className}, bounds=${candidate.bounds}"
                )
            }

            val focused = candidates.firstOrNull { it.node.isFocused }
            if (focused != null) return focused

            val best = findBestEditableCandidate(currentRoot, result)
            if (best != null) return best

            Thread.sleep(intervalMs)
        }

        return null
    }

    private fun setTextToCandidate(
        candidate: ActionCandidate,
        text: String,
        result: RecommendedAction
    ): Boolean {
        // click the input field first
        val tapSuccess = tapRect(candidate.boundsRect)

        Log.d("SetTextAction", "SET_TEXT field tap result: $tapSuccess")

        if (!tapSuccess) {
            return false
        }

        // wait for focus / keyboard / new accessibility tree
        Thread.sleep(500)

        // IMPORTANT: do not use old candidate.node first.
        // Read current root again after tap.
        val newRoot = rootInActiveWindow

        val focusedEditable = findFocusedEditableCandidate(newRoot)
        val bestEditable = findBestEditableCandidate(newRoot, result)

        val targetNode = focusedEditable?.node ?: bestEditable?.node

        if (targetNode == null) {
            Log.d("SetTextAction", "SET_TEXT failed: no editable after field tap")
            return false
        }

        Log.d(
            "SetTextAction",
            "SET_TEXT target after tap: text=${targetNode.text}, class=${targetNode.className}, focused=${targetNode.isFocused}, editable=${targetNode.isEditable}"
        )

        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }

        val success = targetNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            args
        )

        targetNode.refresh()

        Log.d(
            "SetTextAction",
            "SET_TEXT result: $success, afterText=${targetNode.text}"
        )

        return success
    }


    private fun findClickTargetNode(
        root: AccessibilityNodeInfo?,
        result: RecommendedAction
    ): AccessibilityNodeInfo? {
        if (root == null) return null

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
        val targetClass = norm(result.targetClassName)

        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun search(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val nodeText = norm(node.text?.toString())
            val nodeDesc = norm(node.contentDescription?.toString())
            val nodeGroupedText = norm(collectTextFromSubtree(node))
            val nodeClass = norm(node.className?.toString())

            val textMatches =
                (targetText.isNotBlank() && nodeText == targetText) ||
                        (targetText.isNotBlank() && nodeGroupedText == targetText) ||
                        (targetText.isNotBlank() && nodeGroupedText.contains(targetText)) ||
                        (targetText.isNotBlank() && targetText.contains(nodeGroupedText) && nodeGroupedText.isNotBlank())

            val descMatches =
                targetDesc.isNotBlank() && nodeDesc == targetDesc

            val classMatches =
                targetClass.isNotBlank() && nodeClass == targetClass

            val usefulClickNode =
                node.isClickable || findClickableParent(node) != null || findClickableChild(node) != null

            if ((textMatches || descMatches || classMatches) && usefulClickNode) {
                candidates.add(node)
            }

            for (i in 0 until node.childCount) {
                search(node.getChild(i))
            }
        }

        search(root)

        if (candidates.isEmpty()) {
            Log.d(
                "ClickAction",
                "CLICK target not found. targetText=${result.targetText}, targetDesc=${result.targetContentDescription}, targetClass=${result.targetClassName}"
            )
            return null
        }

        return candidates.sortedWith(
            compareByDescending<AccessibilityNodeInfo> {
                norm(it.className?.toString()) == targetClass
            }.thenByDescending {
                it.isClickable
            }.thenByDescending {
                norm(collectTextFromSubtree(it)) == targetText
            }.thenByDescending {
                findClickableParent(it) != null
            }.thenByDescending {
                findClickableChild(it) != null
            }
        ).first()
    }

    private fun findClickableChild(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.isClickable) return node

        for (i in 0 until node.childCount) {
            val found = findClickableChild(node.getChild(i))
            if (found != null) return found
        }

        return null
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
        val targetClass = norm(result.targetClassName)

        fun isExactMatch(nodeText: String, nodeDesc: String, nodeGroupedText: String): Boolean {
            return (targetText.isNotBlank() && nodeText == targetText) ||
                    (targetText.isNotBlank() && nodeGroupedText == targetText) ||
                    (targetDesc.isNotBlank() && nodeDesc == targetDesc)
        }

        fun isContainsMatch(nodeText: String, nodeDesc: String, nodeGroupedText: String): Boolean {
            return (targetText.isNotBlank() && nodeText.contains(targetText)) ||
                    (targetText.isNotBlank() && targetText.contains(nodeText) && nodeText.isNotBlank()) ||
                    (targetText.isNotBlank() && nodeGroupedText.contains(targetText)) ||
                    (targetText.isNotBlank() && targetText.contains(nodeGroupedText) && nodeGroupedText.isNotBlank()) ||
                    (targetDesc.isNotBlank() && nodeDesc.contains(targetDesc)) ||
                    (targetDesc.isNotBlank() && targetDesc.contains(nodeDesc) && nodeDesc.isNotBlank())
        }

        fun search(current: AccessibilityNodeInfo?) {
            if (current == null) return

            val nodeText = norm(current.text?.toString())
            val nodeDesc = norm(current.contentDescription?.toString())
            val nodeGroupedText = norm(collectTextFromSubtree(current))

            if (isExactMatch(nodeText, nodeDesc, nodeGroupedText)) {
                exactMatches.add(current)
            } else if (isContainsMatch(nodeText, nodeDesc, nodeGroupedText)) {
                containsMatches.add(current)
            }

            for (i in 0 until current.childCount) {search(current.getChild(i))}
        }

        search(node)

        val matches = if (exactMatches.isNotEmpty()) exactMatches else containsMatches

        return when {
            matches.isEmpty() -> {
                Log.d(
                    "ScreenService",
                    "Target not found. targetText=${result.targetText}, targetDesc=${result.targetContentDescription}, targetClass=${result.targetClassName}"
                )
                null
            }

            else -> {
                if (matches.size > 1) {
                    Log.d("ScreenService", "Target is ambiguous: ${matches.size} matches found")
                }

                val bestMatch = matches.sortedWith(
                    compareByDescending<AccessibilityNodeInfo> {
                        targetClass.isNotBlank() &&
                                norm(it.className?.toString()) == targetClass
                    }.thenByDescending {
                        it.isClickable
                    }.thenByDescending {
                        findClickableParent(it) != null
                    }
                ).first()

                Log.d(
                    "ScreenService",
                    "Selected target node: text=${bestMatch.text}, desc=${bestMatch.contentDescription}, class=${bestMatch.className}, clickable=${bestMatch.isClickable}"
                )

                bestMatch
            }
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

    private fun findFocusedEditableCandidate(root: AccessibilityNodeInfo?): ActionCandidate? {
        return collectActionCandidates(root)
            .filter { it.editable }
            .firstOrNull { it.node.isFocused }
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


    //get screen context - Image
    //AccessibilityService
    //→ takeScreenshot()
    //→ Bitmap
    //→ resize
    //→ JPEG/WebP compress
    //→ Multipart or Base64
    //→ local VLM server
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshotBytes(): ByteArray? =
        suspendCancellableCoroutine{ continuation ->

            Log.d("VLM Processing", "takeScreenshotBytes() entered...")

            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {

                        override fun onSuccess(result: ScreenshotResult) {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer,
                                result.colorSpace
                            )?.copy(Bitmap.Config.ARGB_8888, false)

                            result.hardwareBuffer.close()

                            if (bitmap == null) {
                                Log.e("VLM Processing", "Screenshot bitmap is null...")
                                continuation.resume(null)
                                return
                            }

                            val resizedBitmap = resizeBitmapKeepRatio(bitmap, maxLongSide = 960)
                            val imageBytes = bitmapToJpegBytes(resizedBitmap, quality = 75)

                            continuation.resume(imageBytes)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e("\"VLM Processing", "Screenshot failed: $errorCode")
                            continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("\"VLM Processing", "takeScreenshot threw exception", e)
                continuation.resume(null)
            }
        }

    private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 75): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    private fun resizeBitmapKeepRatio(bitmap: Bitmap, maxLongSide: Int = 960): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val longestSide = maxOf(width, height)

        if (longestSide <= maxLongSide) { return bitmap }

        val scale = maxLongSide.toFloat() / longestSide

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }


}