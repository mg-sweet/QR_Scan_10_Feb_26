package com.sweet.qr_scan_10_feb_26.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ScannerOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
    }

    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val crossPaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background dim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // Scan box
        val boxWidth = width * 0.65f
        val boxHeight = height * 0.45f

        val left = (width - boxWidth) / 2
        val top = (height - boxHeight) / 2
        val right = left + boxWidth
        val bottom = top + boxHeight

        val rect = RectF(left, top, right, bottom)

        // Clear center
        canvas.drawRect(rect, clearPaint)

        val line = 40f

        // Corners
        canvas.drawLine(left, top, left + line, top, framePaint)
        canvas.drawLine(left, top, left, top + line, framePaint)

        canvas.drawLine(right, top, right - line, top, framePaint)
        canvas.drawLine(right, top, right, top + line, framePaint)

        canvas.drawLine(left, bottom, left + line, bottom, framePaint)
        canvas.drawLine(left, bottom, left, bottom - line, framePaint)

        canvas.drawLine(right, bottom, right - line, bottom, framePaint)
        canvas.drawLine(right, bottom, right, bottom - line, framePaint)

        // Center crosshair
        val cx = width / 2f
        val cy = height / 2f
        val size = 30f

        canvas.drawLine(cx - size, cy, cx + size, cy, crossPaint)
        canvas.drawLine(cx, cy - size, cx, cy + size, crossPaint)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
}