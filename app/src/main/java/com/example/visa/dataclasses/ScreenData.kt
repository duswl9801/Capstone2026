package com.example.visa.dataclasses

enum class UIElementType {
    TEXT_FIELD,
    BUTTON,
    ICON,
    MENU
}

data class UIElement(
    val id: String? = null,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val packageName: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val bounds: String
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