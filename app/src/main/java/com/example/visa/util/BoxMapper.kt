package com.example.visa.util

import com.example.visa.dataclasses.BoundingBox
import kotlin.math.max

// map bounding box coordinates from image space to view space
object BoxMapper {

    fun mapBoxToView(
        box: BoundingBox,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ): BoundingBox {
        val scale = max(
            viewWidth.toFloat() / imageWidth,
            viewHeight.toFloat() / imageHeight
        )

        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale

        val offsetX = (viewWidth - scaledWidth) / 2f
        val offsetY = (viewHeight - scaledHeight) / 2f

        return BoundingBox(
            x1 = (box.x1 * scale + offsetX).toInt(),
            y1 = (box.y1 * scale + offsetY).toInt(),
            x2 = (box.x2 * scale + offsetX).toInt(),
            y2 = (box.y2 * scale + offsetY).toInt()
        )
    }
}