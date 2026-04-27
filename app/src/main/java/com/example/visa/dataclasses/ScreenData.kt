package com.example.visa.dataclasses

enum class UIElementType {
    TEXT_FIELD,
    BUTTON,
    ICON,
    MENU
}

data class UIElement(
    val id: String? = null,
    val elementType: UIElementType, // 0: textfield | 1: button | 2: icon 3: menu
    val uiText: String? = null,
    val location: BoundingBox,
    val isClickable: Boolean,
    val isCheckable: Boolean,
    val isWritable: Boolean,
    var userText: String? = null
)

data class ScreenContext(
    val uies: List<UIElement>,
    val texts: OCRResult,
    val userGoal: String,
    val screenSummary: String,
    val highlightedElements: MutableList<BoundingBox> = mutableListOf()
){
    fun addHighlight(box: BoundingBox) {
        highlightedElements.add(box)
    }

    fun keepOnlyImportantHighlights(importantBoxes: List<BoundingBox>) {
        highlightedElements.clear()
        highlightedElements.addAll(importantBoxes)
    }

    fun clearHighlights() {
        highlightedElements.clear()
    }
}