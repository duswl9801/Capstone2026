package com.example.visa.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

// glow edge to show the app is active in the background
class EdgeGlowView (context: Context) : View(context){
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private var offset = 0f // gradient positoini

    private val animator = ValueAnimator.ofFloat(0f, 2000f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            offset = it.animatedValue as Float
            invalidate()
        }
    }

    init {animator.start()}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val shader = LinearGradient(
            offset,
            0f,
            width.toFloat() + offset,
            height.toFloat(),
            intArrayOf(
                Color.parseColor("#00B8D9"), // clear cyan
                Color.parseColor("#00C853"), // calm green
                Color.parseColor("#2979FF"), // visible blue
                Color.parseColor("#7C4DFF"), // purple
                Color.parseColor("#D500F9"), // magenta-purple
                Color.parseColor("#FFB300"), // warm amber
                Color.parseColor("#00B8D9")  // back to cyan
            ),
            null,
            Shader.TileMode.MIRROR
        )

        paint.shader = shader

        val padding = 8f
        canvas.drawRoundRect(
            padding,
            padding,
            width - padding,
            height - padding,
            36f,
            36f,
            paint
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}