package com.sweet.qr_scan_10_feb_26.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ScannerOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val transparentPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val semiTransparentPaint = Paint().apply {
        color = Color.parseColor("#99000000") // ဘေးပတ်ပတ်လည် မှိန်မယ့်အရောင်
    }

    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. တစ်ပြင်လုံးကို မှိန်ချမယ်
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), semiTransparentPaint)

        // 2. အလယ်ကွက်ကို တွက်ချက်မယ် (220dp ပတ်လည်)
        val size = (150 * resources.displayMetrics.density).toInt()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val right = left + size
        val bottom = top + size
        val rect = RectF(left, top, right, bottom)

        // 3. အလယ်ကွက်ကို ဖောက်ထုတ်မယ် (လင်းသွားစေဖို့)
        canvas.drawRect(rect, transparentPaint)

        // 4. ထောင့်လေးတွေကို ဆွဲမယ် (user ရဲ့ frame ပုံစံအတိုင်း)
        val lineLength = 40f
        // Top-Left
        canvas.drawLine(left, top, left + lineLength, top, framePaint)
        canvas.drawLine(left, top, left, top + lineLength, framePaint)
        // Top-Right
        canvas.drawLine(right, top, right - lineLength, top, framePaint)
        canvas.drawLine(right, top, right, top + lineLength, framePaint)
        // Bottom-Left
        canvas.drawLine(left, bottom, left + lineLength, bottom, framePaint)
        canvas.drawLine(left, bottom, left, bottom - lineLength, framePaint)
        // Bottom-Right
        canvas.drawLine(right, bottom, right - lineLength, bottom, framePaint)
        canvas.drawLine(right, bottom, right, bottom - lineLength, framePaint)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
}