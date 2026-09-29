package com.android.mrzcardreader.camera.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.android.mrzcardscanner.R


class TextOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var frameLeft: Float = 0f
    var frameTop: Float = 0f
    var frameRight: Float = 0f
    var frameBottom: Float = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outPath = Path()
    private val outLinePath = Path()
    private val frameRect = RectF()
    private val graphicLock = Any()
    private val textGraphics: MutableList<TextGraphic> = ArrayList()


    private val outerRegionPaint: Paint = Paint().apply {
        color = Color.parseColor("#D9000000")
        style = Paint.Style.FILL
    }

    private val cardOutline: Paint = Paint().apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 4f.toDp()
        style = Paint.Style.STROKE
    }

    private val instructionPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 16f.toDp()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        attrs?.let { attributeSet ->
            val attributes = context.obtainStyledAttributes(attributeSet, R.styleable.TextOverlay)
            try {
                val backGroundColor =
                    attributes.getColor(R.styleable.TextOverlay_backgroundColor, Color.BLACK)
                outerRegionPaint.apply {
                    color = backGroundColor
                }
            } finally {
                attributes.recycle()
            }
        }
    }

    fun Float.toDp(): Float {
        val density = context.resources.displayMetrics.density
        return this * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        outPath.reset()
        outLinePath.reset()

        val height = height.toFloat()
        val width = width.toFloat()

        outPath.apply {
            moveTo(0f, 0f)
            lineTo(0f, height)
            lineTo(width, height)
            lineTo(width, 0f)
            fillType = Path.FillType.EVEN_ODD
        }

        frameRect.apply {
            left = 2f.toDp()
            top = (height * 0.56f) - 35f.toDp()
            right = width - 2f.toDp()
            bottom = (height * 0.56f) + 35f.toDp()
        }

        frameLeft = frameRect.left
        frameTop = frameRect.top
        frameRight = frameRect.right
        frameBottom = frameRect.bottom

        outLinePath.addRoundRect(
            frameRect,
            16f.toDp(),
            16f.toDp(),
            Path.Direction.CW
        )

        outPath.addPath(outLinePath)

        canvas.drawPath(outPath, outerRegionPaint)
        canvas.drawPath(outLinePath, cardOutline)
        canvas.drawText(
            "Align both MRZ lines inside the box",
            width / 2f,
            frameRect.top - 16f.toDp(),
            instructionPaint
        )

        drawTextOverlays(canvas)
    }

    private fun drawTextOverlays(canvas: Canvas) {
        synchronized(graphicLock) {
            textGraphics.forEach() { textGraphic ->
                textGraphic.draw(canvas)
            }
        }

    }

    fun clear() {
        synchronized(graphicLock) {
            textGraphics.clear()
            postInvalidate()
        }
    }

    fun add(textGraphic: List<TextGraphic>) {
        synchronized(graphicLock) {
            textGraphics.clear()
            textGraphics.addAll(textGraphic)
        }
        postInvalidate()
    }

}
