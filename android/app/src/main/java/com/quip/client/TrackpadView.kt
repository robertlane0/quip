package com.quip.client

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * A touch surface that behaves like a laptop trackpad:
 *  - Single-finger drag  -> relative mouse motion, reported via [onMove]
 *  - Tap                 -> left click, reported via [onLeftClick]
 *  - Double tap           -> right click, reported via [onRightClick]
 *
 * This view only detects gestures and reports deltas/clicks; it knows nothing about
 * QUIP packets or the digital bitmask, keeping it reusable regardless of protocol.
 */
class TrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onMove: ((dx: Float, dy: Float) -> Unit)? = null
    var onLeftClick: (() -> Unit)? = null
    var onRightClick: (() -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f

    // Brief visual "flash" on tap so clicks feel tactile without any real click affordance.
    private var flashAlpha = 0
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            flash()
            onLeftClick?.invoke()
            performClick()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            flash()
            onRightClick?.invoke()
            return true
        }
    })

    init {
        isClickable = true
        isFocusable = true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                onMove?.invoke(dx, dy)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun flash() {
        flashAlpha = 60
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (flashAlpha > 0) {
            flashPaint.alpha = flashAlpha
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
            flashAlpha = (flashAlpha - 8).coerceAtLeast(0)
            if (flashAlpha > 0) invalidate()
        }
    }
}
