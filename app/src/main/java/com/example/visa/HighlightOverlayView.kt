package com.example.visa

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.visa.dataclasses.BoundingBox

// An overlay UI component that draws highlights on the screen
class HighlightOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var boxes: List<BoundingBox> = emptyList()

    private val colors = listOf(
        Color.YELLOW,
        Color.CYAN,
        Color.MAGENTA,
        Color.GREEN,
        Color.RED,
        Color.BLUE
    )

    private val fillPaint  = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    fun setBoxes (newBoxes: List<BoundingBox>) {
        boxes = newBoxes
        invalidate() // draw the canvas again with new Boxes
    }

    fun drawOverlay(canvas: Canvas) {
        boxes.forEachIndexed { index, box ->
            val color = colors[index % colors.size]
            fillPaint.color = Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))
            strokePaint.color = color

            val rect = RectF(
                box.x1.toFloat(),
                box.y1.toFloat(),
                box.x2.toFloat(),
                box.y2.toFloat()
            )

            canvas.drawRect(rect, fillPaint)
            canvas.drawRect(rect, strokePaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOverlay(canvas)
    }
}