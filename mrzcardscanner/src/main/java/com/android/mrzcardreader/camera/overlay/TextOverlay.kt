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
    var mrzFrameLeft: Float = 0f
    var mrzFrameTop: Float = 0f
    var mrzFrameRight: Float = 0f
    var mrzFrameBottom: Float = 0f
    private val outPath = Path()
    private val captureOutlinePath = Path()
    private val mrzOutlinePath = Path()
    private val frameRect = RectF()
    private val mrzRect = RectF()
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

    private val mrzOutline: Paint = Paint().apply {
        color = Color.parseColor("#FFD54F")
        strokeWidth = 3f.toDp()
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
        captureOutlinePath.reset()
        mrzOutlinePath.reset()

        val height = height.toFloat()
        val width = width.toFloat()
        val contentLeft = paddingLeft.toFloat()
        val contentTop = paddingTop.toFloat()
        val contentRight = width - paddingRight
        val contentBottom = height - paddingBottom
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(0f)
        val contentHeight = (contentBottom - contentTop).coerceAtLeast(0f)

        outPath.apply {
            moveTo(0f, 0f)
            lineTo(0f, height)
            lineTo(width, height)
            lineTo(width, 0f)
            fillType = Path.FillType.EVEN_ODD
        }

        val horizontalMargin = 16f.toDp()
        val captureWidth = (contentWidth - (horizontalMargin * 2f)).coerceAtLeast(0f)
        val captureHeight = (captureWidth / 1.42f).coerceAtMost(contentHeight * 0.52f)
        val minimumTop = contentTop + 72f.toDp()
        val maximumTop = (contentBottom - captureHeight - 16f.toDp()).coerceAtLeast(contentTop)
        val captureTop = (contentTop + (contentHeight * 0.48f) - (captureHeight / 2f))
            .coerceIn(minimumTop.coerceAtMost(maximumTop), maximumTop)

        frameRect.set(
            contentLeft + horizontalMargin,
            captureTop,
            contentRight - horizontalMargin,
            captureTop + captureHeight,
        )

        frameLeft = frameRect.left
        frameTop = frameRect.top
        frameRight = frameRect.right
        frameBottom = frameRect.bottom

        val innerMargin = 8f.toDp()
        val mrzHeight = (captureHeight * 0.28f).coerceAtLeast(68f.toDp())
        mrzRect.set(
            frameRect.left + innerMargin,
            frameRect.bottom - innerMargin - mrzHeight,
            frameRect.right - innerMargin,
            frameRect.bottom - innerMargin,
        )
        mrzFrameLeft = mrzRect.left
        mrzFrameTop = mrzRect.top
        mrzFrameRight = mrzRect.right
        mrzFrameBottom = mrzRect.bottom

        captureOutlinePath.addRoundRect(
            frameRect,
            16f.toDp(),
            16f.toDp(),
            Path.Direction.CW
        )
        mrzOutlinePath.addRoundRect(
            mrzRect,
            8f.toDp(),
            8f.toDp(),
            Path.Direction.CW,
        )

        outPath.addPath(captureOutlinePath)

        canvas.drawPath(outPath, outerRegionPaint)
        canvas.drawPath(captureOutlinePath, cardOutline)
        canvas.drawPath(mrzOutlinePath, mrzOutline)
        canvas.drawText(
            "Fit the passport inside the frame",
            contentLeft + (contentWidth / 2f),
            frameRect.top - 16f.toDp(),
            instructionPaint
        )
        canvas.drawText(
            "Align both MRZ lines here",
            contentLeft + (contentWidth / 2f),
            mrzRect.top - 8f.toDp(),
            instructionPaint,
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
