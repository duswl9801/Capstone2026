package com.example.visa.overlay

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class AssistantBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}