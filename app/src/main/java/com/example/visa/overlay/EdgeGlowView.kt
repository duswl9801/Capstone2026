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
        strokeWidth = 10f
    }

    private var offset = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1000f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
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
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED
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