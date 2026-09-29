package com.android.mrzcardreader.camera.overlay

import android.content.Context
import android.graphics.*
import kotlin.math.ceil

class TextGraphic(
    private val context: Context,
    private val imageRect: Rect,
    private val textRect: Rect?,
    private val textOverlay: TextOverlay
) {

    private val painter = Paint()

    fun draw(canvas: Canvas?) {
        canvas?.let { _canvas ->
            painter.color = Color.GREEN
            painter.strokeWidth = 4f
            painter.style = Paint.Style.STROKE
            textRect?.let { _canvas.drawRect(calculateTextRect(it), painter) }
        }
    }

    fun Float.toDp(): Float {
        val density = context.resources.displayMetrics.density
        return this / density
    }

    fun isInsideFrame(): Boolean {
        textRect?.let { rect ->

            val text = calculateTextRect(
                rect
            )
            val centerY = text.centerY()
            return centerY in textOverlay.frameTop..textOverlay.frameBottom &&
                text.right > textOverlay.frameLeft &&
                text.left < textOverlay.frameRight

        } ?: return false

    }

    private fun calculateTextRect(textRect: Rect): RectF {
        val scaleX = textOverlay.width.toFloat() / imageRect.width()
        val scaleY = textOverlay.height.toFloat() / imageRect.height()
        val scale = scaleX.coerceAtLeast(scaleY)

        val offsetX = (textOverlay.width.toFloat() - ceil(imageRect.width() * scale)) / 2.0f
        val offsetY = (textOverlay.height.toFloat() - ceil(imageRect.height() * scale)) / 2.0f

        return RectF().apply {
            left = textRect.left * scale + offsetX
            top = textRect.top * scale + offsetY
            right = textRect.right * scale + offsetX
            bottom = textRect.bottom * scale + offsetY
        }
    }


}
