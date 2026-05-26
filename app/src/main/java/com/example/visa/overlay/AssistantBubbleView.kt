package com.example.visa.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.FrameLayout
import android.graphics.Path
import android.graphics.RectF
import com.example.visa.R

import com.example.visa.util.Utils.dp

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

class SpeechBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val speechPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.visa_soft_beige)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.visa_brown)
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2f)
    }

    private val speechRect = RectF()
    private val tailPath = Path()

    private val cornerRadius = context.dp(18f)
    private val tailWidth = context.dp(24f)
    private val tailHeight = context.dp(14f)

    init {
        setWillNotDraw(false)
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + tailHeight.toInt())// for tail
    }

    override fun onDraw(canvas: Canvas) {
        drawSpeechBubble(canvas)
        super.onDraw(canvas)
    }

    private fun drawSpeechBubble(canvas: Canvas) {
        val borderOffset = borderPaint.strokeWidth / 2f

        speechRect.set(borderOffset, borderOffset, width - borderOffset, height - tailHeight - borderOffset)

        canvas.drawRoundRect(speechRect, cornerRadius, cornerRadius, speechPaint)
        canvas.drawRoundRect(speechRect, cornerRadius, cornerRadius, borderPaint)

        val tailCenterX = width * 0.5f
        val tailTopY = speechRect.bottom
        val tailBottomY = speechRect.bottom + tailHeight

        tailPath.reset()
        tailPath.moveTo(tailCenterX - tailWidth / 2f, tailTopY)
        tailPath.lineTo(tailCenterX, tailBottomY)
        tailPath.lineTo(tailCenterX + tailWidth / 2f, tailTopY)
        tailPath.close()

        canvas.drawPath(tailPath, speechPaint)
        canvas.drawPath(tailPath, borderPaint)
    }

}